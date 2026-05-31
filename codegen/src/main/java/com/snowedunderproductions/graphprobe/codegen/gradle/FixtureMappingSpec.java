package com.snowedunderproductions.graphprobe.codegen.gradle;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixtureMappingSpec {
    private String sql;
    private String csvResource;
    private char delimiter = ',';
    private int linesToSkip = 0;
    private final Map<String, String> arguments = new LinkedHashMap<>();

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getCsvResource() {
        return csvResource;
    }

    public void setCsvResource(String csvResource) {
        this.csvResource = csvResource;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public int getLinesToSkip() {
        return linesToSkip;
    }

    public void setLinesToSkip(int linesToSkip) {
        this.linesToSkip = linesToSkip;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void argument(String argName, String sqlColumn) {
        arguments.put(argName, sqlColumn);
    }
}
