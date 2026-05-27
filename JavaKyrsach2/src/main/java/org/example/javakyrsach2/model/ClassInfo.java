package org.example.javakyrsach2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassInfo {
    private final String packageName;
    private final String simpleName;
    private final String kind;
    private final List<String> fields;
    private final List<String> methods;

    public ClassInfo(String packageName, String simpleName, String kind, List<String> fields, List<String> methods) {
        this.packageName = packageName == null ? "" : packageName;
        this.simpleName = simpleName;
        this.kind = kind;
        this.fields = new ArrayList<>(fields);
        this.methods = new ArrayList<>(methods);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getKind() {
        return kind;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }
}
