package dev.lu212.bv.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class FileUtils {

    private FileUtils() {}

    public static String readFileSafely(Path path) {
        try {
            if (Files.exists(path) && Files.isRegularFile(path) && Files.size(path) < 1024 * 1024) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + path + ": " + e.getMessage());
        }
        return "";
    }

    public static String getProjectStructure(Path projectDir) {
        var sb = new StringBuilder();
        try (var files = Files.walk(projectDir, 5)) {
            files
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("target"))
                .filter(p -> !p.toString().contains("node_modules"))
                .limit(200)
                .forEach(p -> {
                    var rel = projectDir.relativize(p);
                    int depth = rel.getNameCount() - 1;
                    sb.append("  ".repeat(Math.max(0, depth)));
                    sb.append("├─ ").append(rel.getFileName()).append("\n");
                });
        } catch (IOException e) {
            sb.append("(Fehler beim Lesen der Projektstruktur)");
        }
        return sb.toString();
    }

    public static boolean isTextFile(Path path) {
        var name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py") ||
               name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".tsx") ||
               name.endsWith(".jsx") || name.endsWith(".html") || name.endsWith(".css") ||
               name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".yaml") ||
               name.endsWith(".yml") || name.endsWith(".md") || name.endsWith(".txt") ||
               name.endsWith(".properties") || name.endsWith(".cfg") || name.endsWith(".ini") ||
               name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".sql") ||
               name.endsWith(".rs") || name.endsWith(".go") || name.endsWith(".rb") ||
               name.endsWith(".php") || name.endsWith(".c") || name.endsWith(".cpp") ||
               name.endsWith(".h") || name.endsWith(".hpp") || name.endsWith(".swift") ||
               name.endsWith(".gradle") || name.endsWith(".toml") || name.endsWith(".lock") ||
               name.endsWith(".env") || name.equals("Dockerfile") || name.equals("Makefile");
    }
}
