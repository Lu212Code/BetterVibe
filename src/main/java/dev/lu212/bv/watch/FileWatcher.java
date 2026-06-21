package dev.lu212.bv.watch;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class FileWatcher implements AutoCloseable {

    private final Path projectDir;
    private final WatchService watchService;
    private final Set<String> ignorePatterns;
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private final ConcurrentLinkedQueue<ChangeInfo> changes = new ConcurrentLinkedQueue<>();
    private final List<Consumer<ChangeInfo>> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running = true;
    private Thread watchThread;

    private static final Set<String> DEFAULT_IGNORE = Set.of(
        ".git", "target", "node_modules", ".idea", ".mvn", ".gradle",
        "build", "dist", ".settings", "bin", "obj", "out", "__pycache__",
        ".class", ".jar", ".war", ".exe", ".dll", ".so", ".dylib",
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg",
        ".mp3", ".mp4", ".avi", ".mov", ".mkv", ".wav", ".flac",
        ".zip", ".tar", ".gz", ".rar", ".7z",
        ".log", ".lock", ".tmp", ".swp", ".swo"
    );

    public FileWatcher(Path projectDir) {
        this(projectDir, DEFAULT_IGNORE);
    }

    public FileWatcher(Path projectDir, Set<String> ignorePatterns) {
        this.projectDir = projectDir.toAbsolutePath().normalize();
        this.ignorePatterns = new HashSet<>(DEFAULT_IGNORE);
        if (ignorePatterns != null) {
            this.ignorePatterns.addAll(ignorePatterns);
        }
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            registerAll(this.projectDir);
            startWatcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize FileWatcher", e);
        }
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isIgnored(dir)) return FileVisitResult.SKIP_SUBTREE;
                try {
                    var key = dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                    watchKeys.put(key, dir);
                } catch (IOException e) {
                    System.err.println("Cannot watch " + dir + ": " + e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isIgnored(Path path) {
        var name = path.getFileName().toString();
        if (ignorePatterns.contains(name)) return true;
        for (var pattern : ignorePatterns) {
            if (pattern.startsWith(".") && name.endsWith(pattern)) return true;
        }
        return false;
    }

    private void startWatcher() {
        watchThread = new Thread(() -> {
            while (running) {
                try {
                    var key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (key == null) continue;

                    var dir = watchKeys.get(key);
                    if (dir == null) continue;

                    for (var event : key.pollEvents()) {
                        var kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        var filename = (Path) event.context();
                        var fullPath = dir.resolve(filename).toAbsolutePath().normalize();

                        if (isIgnored(fullPath)) continue;

                        ChangeInfo.ChangeType type;
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            type = ChangeInfo.ChangeType.CREATED;
                            if (Files.isDirectory(fullPath) && !isIgnored(fullPath)) {
                                try { registerAll(fullPath); } catch (IOException ignored) {}
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            type = ChangeInfo.ChangeType.MODIFIED;
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            type = ChangeInfo.ChangeType.DELETED;
                        } else {
                            continue;
                        }

                        Path relativePath = projectDir.relativize(fullPath);
                        var diff = type == ChangeInfo.ChangeType.MODIFIED ? readFileContent(fullPath) : "";
                        var change = new ChangeInfo(relativePath, type, diff, System.currentTimeMillis());
                        changes.add(change);
                        notifyListeners(change);
                    }

                    if (!key.reset()) {
                        watchKeys.remove(key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "FileWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private String readFileContent(Path path) {
        try {
            if (Files.size(path) > 1024 * 1024) return ""; // Skip files > 1MB
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }

    public List<ChangeInfo> getAndClearChanges() {
        var result = new ArrayList<ChangeInfo>();
        ChangeInfo change;
        while ((change = changes.poll()) != null) {
            result.add(change);
        }
        return result;
    }

    public String getDiffSinceLastClear() {
        var sb = new StringBuilder();
        ChangeInfo change;
        while ((change = changes.poll()) != null) {
            var marker = switch (change.type()) {
                case CREATED -> "[+]";
                case MODIFIED -> "[~]";
                case DELETED -> "[-]";
            };
            sb.append(marker).append(" ").append(change.file()).append("\n");
            if (change.type() != ChangeInfo.ChangeType.DELETED && !change.diff().isBlank()) {
                sb.append("```\n").append(change.diff().strip()).append("\n```\n\n");
            }
        }
        return sb.toString();
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    public void addListener(Consumer<ChangeInfo> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(ChangeInfo change) {
        for (var l : listeners) {
            try { l.accept(change); } catch (Exception e) { System.err.println("Listener error: " + e); }
        }
    }

    @Override
    public void close() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        try { watchService.close(); } catch (IOException ignored) {}
    }
}
