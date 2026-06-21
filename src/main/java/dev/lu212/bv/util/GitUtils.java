package dev.lu212.bv.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GitUtils {

    private static final int MAX_DIFF_LINES = 500;
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        ".class", ".jar", ".war", ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico",
        ".mp3", ".mp4", ".avi", ".mov", ".mkv", ".zip", ".tar", ".gz", ".rar", ".7z",
        ".exe", ".dll", ".so", ".dylib", ".o", ".a", ".lib",
        ".ttf", ".otf", ".woff", ".woff2", ".eot",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".db", ".sqlite", ".mdb"
    );

    private GitUtils() {}

    public static boolean isGitRepo(Path projectDir) {
        try {
            var process = new ProcessBuilder("git", "rev-parse", "--git-dir")
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public record GitDiffResult(String diff, int filesChanged, int linesChanged, boolean truncated) {}

    public static GitDiffResult getWorkingTreeDiff(Path projectDir) {
        return getWorkingTreeDiff(projectDir, MAX_DIFF_LINES);
    }

    public static GitDiffResult getWorkingTreeDiff(Path projectDir, int maxLines) {
        var allDiffs = new StringBuilder();
        int filesChanged = 0;
        int linesChanged = 0;
        boolean truncated = false;

        for (var ref : new String[]{null, "--cached"}) {
            var cmd = new ArrayList<>(List.of("git", "diff"));
            if (ref != null) cmd.add(ref);
            cmd.add("--diff-filter=ACMR");
            cmd.add("--no-color");
            var result = runGit(projectDir, cmd);
            if (result == null || result.isBlank()) continue;

            var sections = result.split("(?=^diff --git)", -1);
            for (var section : sections) {
                if (section.isBlank()) continue;
                if (isBinaryDiff(section)) continue;

                if (allDiffs.length() + section.length() > maxLines * 80) {
                    var remaining = (maxLines * 80) - allDiffs.length();
                    if (remaining > 0) {
                        allDiffs.append(section, 0, Math.min(section.length(), remaining));
                        allDiffs.append("\n... (gekurzt)");
                    }
                    truncated = true;
                    break;
                }

                allDiffs.append(section);
                filesChanged++;

                var lines = section.split("\n");
                for (var line : lines) {
                    if (line.startsWith("+") && !line.startsWith("+++")) linesChanged++;
                    if (line.startsWith("-") && !line.startsWith("---")) linesChanged++;
                }
            }
            if (truncated) break;
        }

        return new GitDiffResult(allDiffs.toString(), filesChanged, linesChanged, truncated);
    }

    public static GitDiffResult getDiffSinceCommit(Path projectDir, String commit) {
        var cmd = List.of("git", "diff", commit, "--no-color");
        var result = runGit(projectDir, cmd);
        if (result == null || result.isBlank()) return new GitDiffResult("", 0, 0, false);

        var sb = new StringBuilder();
        int filesChanged = 0;
        int linesChanged = 0;
        boolean truncated = false;

        var sections = result.split("(?=^diff --git)", -1);
        for (var section : sections) {
            if (section.isBlank()) continue;
            if (isBinaryDiff(section)) continue;

            if (sb.length() + section.length() > MAX_DIFF_LINES * 80) {
                var remaining = (MAX_DIFF_LINES * 80) - sb.length();
                if (remaining > 0) {
                    sb.append(section, 0, Math.min(section.length(), remaining));
                    sb.append("\n... (gekurzt)");
                }
                truncated = true;
                break;
            }

            sb.append(section);
            filesChanged++;
            var lines = section.split("\n");
            for (var line : lines) {
                if (line.startsWith("+") && !line.startsWith("+++")) linesChanged++;
                if (line.startsWith("-") && !line.startsWith("---")) linesChanged++;
            }
        }

        return new GitDiffResult(sb.toString(), filesChanged, linesChanged, truncated);
    }

    private static String runGit(Path projectDir, List<String> cmd) {
        try {
            var process = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start();
            var output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) return null;
            return output;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static boolean isBinaryDiff(String section) {
        for (var ext : BINARY_EXTENSIONS) {
            if (section.contains(ext + " |")) return true;
        }
        var lines = section.split("\n");
        for (var line : lines) {
            if (line.contains("Binary files") && line.contains("differ")) return true;
        }
        return false;
    }
}
