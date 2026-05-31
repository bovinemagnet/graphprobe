package com.snowedunderproductions.graphprobe.codegen.gradle;

import org.gradle.api.Action;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixtureMappingsDsl {
    private final Map<String, FixtureMappingSpec> mappings = new LinkedHashMap<>();

    public void operation(String operationName, Action<FixtureMappingSpec> action) {
        FixtureMappingSpec spec = mappings.computeIfAbsent(operationName, ignored -> new FixtureMappingSpec());
        action.execute(spec);
    }

    public Map<String, FixtureMappingSpec> getMappings() {
        return mappings;
    }
}
