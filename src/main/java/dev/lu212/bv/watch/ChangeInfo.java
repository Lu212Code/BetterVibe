package dev.lu212.bv.watch;

import java.nio.file.Path;
import java.util.List;

public record ChangeInfo(Path file, ChangeType type, String diff, long timestamp) {
    public enum ChangeType { CREATED, MODIFIED, DELETED }
}
