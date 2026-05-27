package org.example.javakyrsach2.db;

import java.time.LocalDateTime;

public record AnalysisSummary(
        long id,
        String projectPath,
        int classCount,
        int relationCount,
        LocalDateTime createdAt
) {
    @Override
    public String toString() {
        return "#" + id + " | " + createdAt + " | классов: " + classCount + ", связей: " + relationCount;
    }
}
