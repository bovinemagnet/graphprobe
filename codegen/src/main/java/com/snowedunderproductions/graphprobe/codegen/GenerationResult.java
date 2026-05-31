package com.snowedunderproductions.graphprobe.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerationResult {
    private final List<Path> generatedFiles = new ArrayList<>();
    private final List<String> skippedOperations = new ArrayList<>();

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    /**
     * Names of query operations that matched the include/exclude filters but were skipped because
     * the configured {@code maxOperations} limit was reached. Empty when nothing was truncated.
     */
    public List<String> getSkippedOperations() {
        return skippedOperations;
    }
}
