package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.FieldDefinition;

record GeneratedOperation(String operationType, String rootTypeName, FieldDefinition field) {
    String fieldName() {
        return field.getName();
    }

    String qualifiedName() {
        return rootTypeName + "." + fieldName();
    }
}
