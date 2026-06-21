package dev.lu212.bv.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void isGitRepo_returnsFalseForNonGitDir() {
        assertFalse(GitUtils.isGitRepo(tempDir));
    }

    @Test
    void isGitRepo_returnsTrueForGitDir() throws Exception {
        runGit("init");
        assertTrue(GitUtils.isGitRepo(tempDir));
    }

    @Test
    void getWorkingTreeDiff_returnsEmptyForCleanRepo() throws Exception {
        runGit("init");
        assertEquals("", GitUtils.getWorkingTreeDiff(tempDir).diff());
    }

    @Test
    void getWorkingTreeDiff_detectsUnstagedChanges() throws Exception {
        initAndCommit();
        var file = tempDir.resolve("existing.txt");
        Files.writeString(file, "modified content");

        var result = GitUtils.getWorkingTreeDiff(tempDir);
        assertNotNull(result);
        assertFalse(result.diff().isBlank());
        assertTrue(result.filesChanged() > 0);
    }

    @Test
    void getWorkingTreeDiff_detectsNewFile() throws Exception {
        initAndCommit();
        var file = tempDir.resolve("newfile.txt");
        Files.writeString(file, "new file content");
        runGit("add", ".");

        var result = GitUtils.getWorkingTreeDiff(tempDir);
        assertNotNull(result);
        assertFalse(result.diff().isBlank());
        assertTrue(result.filesChanged() > 0);
    }

    @Test
    void getWorkingTreeDiff_truncatesLargeDiffs() throws Exception {
        initAndCommit();
        var file = tempDir.resolve("large.txt");
        var sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("line ").append(i).append(" ");
            sb.append("x".repeat(100));
            sb.append("\n");
        }
        Files.writeString(file, sb.toString());

        var result = GitUtils.getWorkingTreeDiff(tempDir, 10);
        assertTrue(result.truncated() || result.diff().lines().count() <= 15);
    }

    private void initAndCommit() throws Exception {
        runGit("init");
        runGit("config", "user.email", "test@test.com");
        runGit("config", "user.name", "Test");
        var initial = tempDir.resolve("existing.txt");
        Files.writeString(initial, "hello");
        runGit("add", ".");
        runGit("commit", "-m", "initial");
    }

    private void runGit(String... args) throws Exception {
        var list = new java.util.ArrayList<String>();
        list.add("git");
        list.addAll(java.util.List.of(args));
        var pb = new ProcessBuilder(list)
            .directory(tempDir.toFile())
            .redirectErrorStream(true);
        var proc = pb.start();
        int rc = proc.waitFor();
        if (rc != 0) throw new IOException(
            "git " + String.join(" ", args) + " failed: " + rc +
            "\n" + new String(proc.getInputStream().readAllBytes()));
    }
}
