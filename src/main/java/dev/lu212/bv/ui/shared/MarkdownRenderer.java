package dev.lu212.bv.ui.shared;

import java.util.regex.Pattern;

public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    public static String toHtml(String markdown) {
        var html = markdown;
        html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        html = html.replace("\"", "&quot;");

        html = html.replaceAll("```(\\w*)\\n([\\s\\S]*?)```", "<pre><code>$2</code></pre>");
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        var lines = html.split("\n", -1);
        var sb = new StringBuilder();
        boolean inList = false;
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.startsWith("- ")) {
                if (!inList) { sb.append("<ul>"); inList = true; }
                sb.append("<li>").append(trimmed.substring(2)).append("</li>");
            } else {
                if (inList) { sb.append("</ul>"); inList = false; }
                if (trimmed.startsWith("<pre>")) {
                    sb.append(trimmed);
                } else if (!trimmed.isEmpty()) {
                    sb.append("<p>").append(trimmed).append("</p>");
                }
            }
        }
        if (inList) sb.append("</ul>");
        return sb.toString();
    }

    public static String htmlTemplate(String bodyHtml) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8">
            <style>
                body { margin: 0; padding: 6px; font-family: 'Segoe UI', 'System', sans-serif; font-size: 12px; color: #cccccc; background: #1a1a1a; }
                p { margin: 2px 0; }
                pre { background: #111; padding: 6px; border-radius: 4px; overflow-x: auto; margin: 4px 0; font-size: 11px; }
                code { background: #111; padding: 1px 3px; border-radius: 3px; font-size: 11px; }
                pre code { background: none; padding: 0; }
                ul { margin: 2px 0; padding-left: 18px; }
                li { margin: 1px 0; }
                a { color: #4a7fa5; }
                strong { color: #e0e0e0; }
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(bodyHtml);
    }
}
