package dev.lu212.bv.indexer;

import dev.lu212.bv.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TodoScanner {

    private static final Pattern TODO_PATTERN = Pattern.compile(
        "(?i)(?:TODO|FIXME|HACK|XXX|BUG|WORKAROUND|OPTIMIZE|REFACTOR)\\b[^\\n]*",
        Pattern.MULTILINE
    );
    private static final Pattern TAG_PATTERN = Pattern.compile(
        "(?i)(TODO|FIXME|HACK|XXX|BUG|WORKAROUND|OPTIMIZE|REFACTOR)"
    );

    private TodoScanner() {}

    public record TodoItem(String tag, String message, String file, int line) {}

    public static List<TodoItem> scanProject(Path projectDir) {
        var todos = new ArrayList<TodoItem>();
        try (var files = Files.walk(projectDir, 20)) {
            files
                .filter(Files::isRegularFile)
                .filter(p -> FileUtils.isTextFile(p))
                .filter(p -> {
                    var s = p.toString();
                    return !s.contains(".git") && !s.contains("target") && !s.contains("node_modules");
                })
                .forEach(file -> {
                    try {
                        var content = Files.readString(file);
                        var lines = content.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            var matcher = TODO_PATTERN.matcher(lines[i]);
                            while (matcher.find()) {
                                var tagMatcher = TAG_PATTERN.matcher(matcher.group());
                                var tag = tagMatcher.find() ? tagMatcher.group(1).toUpperCase() : "TODO";
                                var msg = matcher.group().strip();
                                var relPath = projectDir.relativize(file).toString();
                                todos.add(new TodoItem(tag, msg, relPath, i + 1));
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Error scanning todos in " + file + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error scanning project for todos: " + e.getMessage());
        }
        return todos;
    }

    public static String formatTodos(List<TodoItem> todos) {
        if (todos == null || todos.isEmpty()) return "";
        var sb = new StringBuilder();
        sb.append("## Gefundene Aufgaben im Code\n");
        for (var todo : todos) {
            sb.append("- `").append(todo.file()).append(":").append(todo.line()).append("` ")
              .append("**[").append(todo.tag()).append("]** ")
              .append(todo.message()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
