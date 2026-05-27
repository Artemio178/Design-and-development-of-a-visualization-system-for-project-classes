package org.example.javakyrsach2.export;

import org.example.javakyrsach2.model.RelationType;

import java.util.List;

public record DiagramRenderSnapshot(
        double width,
        double height,
        List<ClassBlock> classes,
        List<EdgeSegment> edges,
        Legend legend
) {
    public record ClassBlock(
            double x,
            double y,
            double width,
            double height,
            boolean interfaceType,
            List<String> lines
    ) {
    }

    public record EdgeSegment(
            double startX,
            double startY,
            double endX,
            double endY,
            RelationType relationType,
            String label,
            double labelX,
            double labelY
    ) {
    }

    public record Legend(
            double x,
            double y,
            double width,
            double height,
            List<String> lines
    ) {
    }
}
