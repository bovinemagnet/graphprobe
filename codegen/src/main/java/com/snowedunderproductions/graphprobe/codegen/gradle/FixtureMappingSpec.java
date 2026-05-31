package com.snowedunderproductions.graphprobe.codegen.gradle;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixtureMappingSpec {
    private String sql;
    private final Map<String, String> arguments = new LinkedHashMap<>();

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void argument(String argName, String sqlColumn) {
        arguments.put(argName, sqlColumn);
    }
}
