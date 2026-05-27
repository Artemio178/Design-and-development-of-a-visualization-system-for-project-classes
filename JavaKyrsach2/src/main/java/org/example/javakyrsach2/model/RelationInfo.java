package org.example.javakyrsach2.model;

public class RelationInfo {
    private final String sourceName;
    private final String targetName;
    private final RelationType relationType;

    public RelationInfo(String sourceName, String targetName, RelationType relationType) {
        this.sourceName = sourceName;
        this.targetName = targetName;
        this.relationType = relationType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public RelationType getRelationType() {
        return relationType;
    }
}
