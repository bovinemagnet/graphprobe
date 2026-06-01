package com.snowedunderproductions.graphprobe.allure.gradle;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Pure {@code java.nio.file} helpers for the Allure tasks.
 *
 * <p>Kept free of any Gradle {@code Project} access so task actions calling
 * these remain configuration-cache safe.
 */
final class FileOps {

    private FileOps() {
    }

    /** Recursively delete {@code dir} if it exists. No-op when absent. */
    static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Recursively copy {@code source} into {@code target}, optionally skipping any
     * directory whose name equals {@code excludeDirName} (at any depth). Existing
     * files are overwritten. Creates {@code target} if needed.
     */
    static void copyRecursively(Path source, Path target, String excludeDirName) throws IOException {
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (excludeDirName != null && !dir.equals(source)
                        && dir.getFileName().toString().equals(excludeDirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Whether {@code dir} exists, is a directory and contains at least one entry. */
    static boolean isNonEmptyDirectory(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isPresent();
        }
    }
}
