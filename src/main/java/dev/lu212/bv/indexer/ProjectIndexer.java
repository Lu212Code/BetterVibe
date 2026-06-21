package dev.lu212.bv.indexer;

import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.ai.Message;
import dev.lu212.bv.db.ProjectIndexRepository;
import dev.lu212.bv.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectIndexer {

    private static final int MAX_FILE_SIZE = 512 * 1024;
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:public\\s+|private\\s+|protected\\s+)?(?:abstract\\s+|final\\s+|static\\s+)?(?:class|interface|enum|record)\\s+(\\w+)(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[^{]+)?\\s*\\{",
        Pattern.DOTALL
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:public|private|protected|static|final|synchronized|abstract|default|native)\\s+" +
        "(?:<[^>]+>\\s*)?" +
        "(?:\\w+(?:<[^>]+>)?\\[?\\]?\\s+)" +
        "(\\w+)\\s*\\(",
        Pattern.DOTALL
    );
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
        "^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE
    );

    private final ProjectIndexRepository repo;
    private final AIProvider aiProvider;
    private final String aiModelId;
    private final Path projectPath;

    public ProjectIndexer(ProjectIndexRepository repo, AIProvider aiProvider, String aiModelId, Path projectPath) {
        this.repo = repo;
        this.aiProvider = aiProvider;
        this.aiModelId = aiModelId;
        this.projectPath = projectPath;
    }

    public record IndexProgress(int current, int total, String currentFile) {}

    public void indexProject(Consumer<IndexProgress> onProgress, Runnable onComplete, Consumer<String> onError) {
        var sourceFiles = collectSourceFiles(projectPath);
        int total = sourceFiles.size();
        var counter = new AtomicInteger(0);

        if (total == 0) {
            repo.markIndexed(projectPath.toString(), 0);
            onComplete.run();
            return;
        }

        for (var file : sourceFiles) {
            int current = counter.incrementAndGet();
            onProgress.accept(new IndexProgress(current, total, projectPath.relativize(file).toString()));

            try {
                var content = Files.readString(file);
                var relPath = projectPath.relativize(file).toString();
                var classNames = extractClasses(content);
                var methods = extractMethods(content);

                if (classNames.isEmpty() && methods.isEmpty()) continue;

                var description = describeClass(file, content, classNames);

                for (var cls : classNames) {
                    var methodsJson = String.join(", ", methods);
                    repo.saveClassInfo(projectPath.toString(), cls, relPath, description, methodsJson);
                }
            } catch (IOException e) {
                System.err.println("Error indexing " + file + ": " + e.getMessage());
            }
        }

        repo.markIndexed(projectPath.toString(), total);
        onComplete.run();
    }

    public void indexProjectBasic(Consumer<IndexProgress> onProgress, Runnable onComplete, Consumer<String> onError) {
        var sourceFiles = collectSourceFiles(projectPath);
        int total = sourceFiles.size();
        var counter = new AtomicInteger(0);

        if (total == 0) {
            repo.markIndexed(projectPath.toString(), 0);
            onComplete.run();
            return;
        }

        for (var file : sourceFiles) {
            int current = counter.incrementAndGet();
            onProgress.accept(new IndexProgress(current, total, projectPath.relativize(file).toString()));

            try {
                var content = Files.readString(file);
                var relPath = projectPath.relativize(file).toString();
                var classNames = extractClasses(content);
                var methods = extractMethods(content);

                if (classNames.isEmpty() && methods.isEmpty()) continue;

                for (var cls : classNames) {
                    var methodsJson = String.join(", ", methods);
                    repo.saveClassInfo(projectPath.toString(), cls, relPath, "", methodsJson);
                }
            } catch (IOException e) {
                System.err.println("Error indexing " + file + ": " + e.getMessage());
            }
        }

        repo.markIndexed(projectPath.toString(), total);
        onComplete.run();
    }

    private List<Path> collectSourceFiles(Path dir) {
        var result = new ArrayList<Path>();
        try (var files = Files.walk(dir, 20)) {
            files
                .filter(Files::isRegularFile)
                .filter(p -> FileUtils.isTextFile(p))
                .filter(p -> {
                    var s = p.toString();
                    return !s.contains(".git") && !s.contains("target") && !s.contains("node_modules")
                        && !s.contains(".mvn") && !s.contains(".gradle");
                })
                .forEach(result::add);
        } catch (IOException e) {
            System.err.println("Error collecting source files: " + e.getMessage());
        }
        return result;
    }

    private List<String> extractClasses(String content) {
        var result = new ArrayList<String>();
        var matcher = CLASS_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private List<String> extractMethods(String content) {
        var result = new ArrayList<String>();
        var matcher = METHOD_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String describeClass(Path file, String content, List<String> classNames) {
        if (aiProvider == null || !aiProvider.isAvailable() || aiModelId == null) {
            return "";
        }
        try {
            var packageName = extractPackage(content);
            var prompt = new StringBuilder();
            prompt.append("Beschreibe in 1-2 Sätzen auf Deutsch, was die Klasse ");
            if (!packageName.isEmpty()) prompt.append(packageName).append(".");
            prompt.append(String.join(", ", classNames));
            prompt.append(" in folgendem Code macht. Nenne nur die Beschreibung, nichts sonst.\n\n```\n");
            prompt.append(content.length() > 3000 ? content.substring(0, 3000) + "\n..." : content);
            prompt.append("\n```");

            var response = aiProvider.chat(
                List.of(new Message("system", "Du bist ein erfahrener Softwareentwickler, der präzise und kurze Klassendokumentation schreibt."),
                        new Message("user", prompt.toString())),
                aiModelId
            );
            return response.content().strip();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractPackage(String content) {
        var m = PACKAGE_PATTERN.matcher(content);
        return m.find() ? m.group(1) : "";
    }
}
