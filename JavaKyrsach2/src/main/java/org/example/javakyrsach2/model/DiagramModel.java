package org.example.javakyrsach2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiagramModel {
    private final List<ClassInfo> classes;
    private final List<RelationInfo> relations;

    public DiagramModel(List<ClassInfo> classes, List<RelationInfo> relations) {
        this.classes = new ArrayList<>(classes);
        this.relations = new ArrayList<>(relations);
    }

    public List<ClassInfo> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    public List<RelationInfo> getRelations() {
        return Collections.unmodifiableList(relations);
    }
}
