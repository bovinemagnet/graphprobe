package com.snowedunderproductions.graphprobe.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerationResult {
    private final List<Path> generatedFiles = new ArrayList<>();

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }
}
