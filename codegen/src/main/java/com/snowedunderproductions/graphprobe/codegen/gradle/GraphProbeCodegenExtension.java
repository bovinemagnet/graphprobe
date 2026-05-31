package com.snowedunderproductions.graphprobe.codegen.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class GraphProbeCodegenExtension {
    private final FixtureMappingsDsl fixtureMappingsDsl;

    @Inject
    public GraphProbeCodegenExtension(ObjectFactory objects) {
        this.fixtureMappingsDsl = objects.newInstance(FixtureMappingsDsl.class);
    }

    public abstract ConfigurableFileCollection getSchemaFiles();

    public abstract Property<String> getBasePackage();

    public abstract Property<String> getDgsCodegenPackage();

    public abstract DirectoryProperty getOutputDirectory();

    public abstract DirectoryProperty getPersistentOutputDirectory();

    public abstract RegularFileProperty getFixtureMappingsFile();

    public abstract ListProperty<String> getOperationIncludePatterns();

    public abstract ListProperty<String> getOperationExcludePatterns();

    public abstract Property<Integer> getMaxOperations();

    public abstract Property<String> getTestStyle();

    public void fixtureMappings(Action<FixtureMappingsDsl> action) {
        action.execute(fixtureMappingsDsl);
    }

    public FixtureMappingsDsl getFixtureMappingsDsl() {
        return fixtureMappingsDsl;
    }
}
