package com.snowedunderproductions.graphprobe.codegen;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixtureMapping {
    private String sql;
    private Map<String, String> arguments = new LinkedHashMap<>();

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }
}
