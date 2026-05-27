package org.example.javakyrsach2.ui;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import org.example.javakyrsach2.export.DiagramPngExporter;
import org.example.javakyrsach2.export.DiagramRenderSnapshot;
import org.example.javakyrsach2.export.DiagramSvgWriter;
import org.example.javakyrsach2.model.ClassInfo;
import org.example.javakyrsach2.model.DiagramModel;
import org.example.javakyrsach2.model.RelationInfo;
import org.example.javakyrsach2.model.RelationType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DiagramPane extends Pane {
    private static final double BOX_WIDTH = 240;
    private static final double H_GAP = 70;
    private static final double V_GAP = 90;
    private static final double MARGIN = 40;
    private static final double LEGEND_WIDTH = 300;
    private static final double EDGE_SLOT_SPACING = 32;
    private static final double EDGE_BORDER_MARGIN = 14;
    private static final int LARGE_PROJECT_THRESHOLD = 10;
    private static final int MAX_CLASSES_PER_ROW = 6;
    private static final int LARGE_LAYOUT_TOP_OFFSET = 120;
    private static final int LARGE_LAYOUT_ROW_GAP = 40;

    private static final Color COLOR_INHERITANCE = Color.web("#1a1a1a");
    private static final Color COLOR_IMPLEMENTATION = Color.web("#1b7a3d");
    private static final Color COLOR_ASSOCIATION = Color.web("#2c4f9e");
    private static final Color COLOR_DEPENDENCY = Color.web("#8a3db0");

    private DiagramRenderSnapshot renderSnapshot;

    public boolean canExport() {
        return renderSnapshot != null;
    }

    public void exportToPng(Path path) throws IOException {
        if (renderSnapshot == null) {
            throw new IllegalStateException("Сначала постройте диаграмму.");
        }
        DiagramPngExporter.export(this, path);
    }

    public void exportToSvg(Path path) throws IOException {
        if (renderSnapshot == null) {
            throw new IllegalStateException("Сначала постройте диаграмму.");
        }
        DiagramSvgWriter.write(renderSnapshot, path);
    }

    public void setModel(DiagramModel model) {
        getChildren().clear();
        renderSnapshot = null;

        List<ClassInfo> classes = model.getClasses();
        if (classes.isEmpty()) {
            return;
        }

        Group nodesLayer = new Group();
        Group edgesLayer = new Group();
        Group legendLayer = new Group();
        getChildren().addAll(nodesLayer, edgesLayer, legendLayer);

        Map<String, Bounds> boundsMap = new HashMap<>();
        boolean largeProject = classes.size() > LARGE_PROJECT_THRESHOLD;
        Map<String, Integer> layers = assignLayers(classes, model.getRelations(), largeProject);
        Map<String, Point2D> positions = positionByLayers(classes, layers, model.getRelations(), largeProject);

        for (ClassInfo classInfo : classes) {
            Point2D pos = positions.get(classInfo.getSimpleName());
            if (pos == null) {
                continue;
            }
            VBox box = createClassBox(classInfo);
            box.relocate(pos.getX(), pos.getY());
            nodesLayer.getChildren().add(box);
            box.applyCss();
            box.layout();
            boundsMap.put(classInfo.getSimpleName(),
                    new BoundingBox(pos.getX(), pos.getY(), BOX_WIDTH, box.prefHeight(BOX_WIDTH)));
        }

        List<RelationLayout> relationLayouts = new ArrayList<>();
        for (RelationInfo relation : model.getRelations()) {
            Bounds source = boundsMap.get(relation.getSourceName());
            Bounds target = boundsMap.get(relation.getTargetName());
            if (source == null || target == null) {
                continue;
            }
            SidePair sides = chooseSides(source, target, relation.getRelationType());
            relationLayouts.add(new RelationLayout(relation, source, target, sides.sourceSide(), sides.targetSide()));
        }

        assignConnectionSlots(relationLayouts, true);
        assignConnectionSlots(relationLayouts, false);

        for (RelationLayout layout : relationLayouts) {
            drawRelation(edgesLayer, layout);
        }

        VBox legend = createLegend();
        legend.relocate(MARGIN, MARGIN);
        legendLayer.getChildren().add(legend);

        double contentWidth = computeMaxX(boundsMap) + MARGIN + LEGEND_WIDTH;
        double contentHeight = Math.max(computeMaxY(boundsMap) + MARGIN, legend.prefHeight(-1) + 2 * MARGIN);
        setPrefSize(contentWidth, contentHeight);
        renderSnapshot = buildRenderSnapshot(
                model,
                boundsMap,
                relationLayouts,
                contentWidth,
                contentHeight,
                legend
        );
    }

    private DiagramRenderSnapshot buildRenderSnapshot(
            DiagramModel model,
            Map<String, Bounds> boundsMap,
            List<RelationLayout> relationLayouts,
            double contentWidth,
            double contentHeight,
            VBox legend
    ) {
        Map<String, ClassInfo> classByName = model.getClasses().stream()
                .collect(Collectors.toMap(ClassInfo::getSimpleName, c -> c, (a, b) -> a, LinkedHashMap::new));

        List<DiagramRenderSnapshot.ClassBlock> classBlocks = new ArrayList<>();
        for (Map.Entry<String, Bounds> entry : boundsMap.entrySet()) {
            ClassInfo classInfo = classByName.get(entry.getKey());
            if (classInfo == null) {
                continue;
            }
            Bounds bounds = entry.getValue();
            classBlocks.add(new DiagramRenderSnapshot.ClassBlock(
                    bounds.getMinX(),
                    bounds.getMinY(),
                    bounds.getWidth(),
                    bounds.getHeight(),
                    isInterface(classInfo),
                    classLinesFrom(classInfo)
            ));
        }

        List<DiagramRenderSnapshot.EdgeSegment> edges = new ArrayList<>();
        for (RelationLayout layout : relationLayouts) {
            AnchorPoints anchors = computeAnchorPoints(layout);
            RelationType relationType = layout.relation().getRelationType();
            edges.add(new DiagramRenderSnapshot.EdgeSegment(
                    anchors.start().getX(),
                    anchors.start().getY(),
                    anchors.end().getX(),
                    anchors.end().getY(),
                    relationType,
                    labelFor(relationType),
                    anchors.labelPosition().getX(),
                    anchors.labelPosition().getY()
            ));
        }

        legend.applyCss();
        legend.layout();
        DiagramRenderSnapshot.Legend legendSnapshot = new DiagramRenderSnapshot.Legend(
                MARGIN,
                MARGIN,
                LEGEND_WIDTH,
                legend.prefHeight(-1),
                legendLines()
        );

        return new DiagramRenderSnapshot(contentWidth, contentHeight, classBlocks, edges, legendSnapshot);
    }

    private List<String> classLinesFrom(ClassInfo classInfo) {
        List<String> lines = new ArrayList<>();
        boolean interfaceType = isInterface(classInfo);
        String stereotype = interfaceType ? "interface" : classInfo.getKind();
        lines.add("«" + stereotype + "» " + classInfo.getSimpleName());

        if (!classInfo.getPackageName().isEmpty()) {
            lines.add(classInfo.getPackageName());
        }
        if (!classInfo.getFields().isEmpty()) {
            lines.add("— поля —");
            lines.addAll(classInfo.getFields());
        }
        if (!classInfo.getMethods().isEmpty()) {
            lines.add("— методы —");
            lines.addAll(classInfo.getMethods());
        }
        return lines;
    }

    private List<String> legendLines() {
        return List.of(
                "Условные обозначения",
                "наследует — от источника к цели",
                "реализует — от источника к цели",
                "использует — от источника к цели",
                "зависит от — от источника к цели",
                "Стрелка указывает на цель связи"
        );
    }

    private Map<String, Integer> assignLayers(
            List<ClassInfo> classes,
            List<RelationInfo> relations,
            boolean largeProject
    ) {
        if (largeProject) {
            return assignLayersForLargeProject(classes, relations);
        }
        return assignLayersForSmallProject(classes, relations);
    }

    private Map<String, Integer> assignLayersForSmallProject(List<ClassInfo> classes, List<RelationInfo> relations) {
        Map<String, Integer> layer = new LinkedHashMap<>();
        for (ClassInfo classInfo : classes) {
            layer.put(classInfo.getSimpleName(), 0);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (RelationInfo relation : relations) {
                if (!isHierarchyRelation(relation.getRelationType())) {
                    continue;
                }
                String source = relation.getSourceName();
                String target = relation.getTargetName();
                int newLayer = Math.max(layer.get(source), layer.get(target) + 1);
                if (newLayer != layer.get(source)) {
                    layer.put(source, newLayer);
                    changed = true;
                }
            }
        }

        for (RelationInfo relation : relations) {
            if (relation.getRelationType() != RelationType.ASSOCIATION) {
                continue;
            }
            String source = relation.getSourceName();
            String target = relation.getTargetName();
            int sourceLayer = layer.get(source);
            int targetLayer = layer.get(target);
            if (targetLayer < sourceLayer) {
                layer.put(target, sourceLayer);
            }
        }

        return layer;
    }

    private Map<String, Integer> assignLayersForLargeProject(List<ClassInfo> classes, List<RelationInfo> relations) {
        Set<String> inHierarchy = new HashSet<>();
        for (RelationInfo relation : relations) {
            if (isHierarchyRelation(relation.getRelationType())) {
                inHierarchy.add(relation.getSourceName());
                inHierarchy.add(relation.getTargetName());
            }
        }

        Map<String, ClassInfo> classByName = classes.stream()
                .collect(Collectors.toMap(ClassInfo::getSimpleName, c -> c, (a, b) -> a, LinkedHashMap::new));

        Map<String, Integer> layer = new LinkedHashMap<>();
        for (ClassInfo classInfo : classes) {
            String name = classInfo.getSimpleName();
            if (isInterface(classInfo)) {
                layer.put(name, 0);
            } else if (inHierarchy.contains(name)) {
                layer.put(name, 1);
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (RelationInfo relation : relations) {
                if (!isHierarchyRelation(relation.getRelationType())) {
                    continue;
                }
                String source = relation.getSourceName();
                String target = relation.getTargetName();

                if (!layer.containsKey(target)) {
                    ClassInfo targetClass = classByName.get(target);
                    layer.put(target, targetClass != null && isInterface(targetClass) ? 0 : 1);
                }

                int newSourceLayer = layer.get(target) + 1;
                int currentSourceLayer = layer.getOrDefault(source, 1);
                if (newSourceLayer > currentSourceLayer) {
                    layer.put(source, newSourceLayer);
                    changed = true;
                }
            }
        }

        int maxHierarchyLayer = layer.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int utilityLayer = maxHierarchyLayer + 1;

        for (ClassInfo classInfo : classes) {
            String name = classInfo.getSimpleName();
            if (!inHierarchy.contains(name) && !isInterface(classInfo)) {
                layer.put(name, utilityLayer);
            } else if (!layer.containsKey(name)) {
                layer.put(name, utilityLayer);
            }
        }

        return layer;
    }

    private Map<String, Point2D> positionByLayers(
            List<ClassInfo> classes,
            Map<String, Integer> layers,
            List<RelationInfo> relations,
            boolean largeProject
    ) {
        if (largeProject) {
            return positionLargeProject(classes, layers);
        }
        return positionSmallProject(classes, layers, relations);
    }

    private Map<String, Point2D> positionSmallProject(
            List<ClassInfo> classes,
            Map<String, Integer> layers,
            List<RelationInfo> relations
    ) {
        Map<String, Long> incomingAssociation = new HashMap<>();
        for (RelationInfo relation : relations) {
            if (relation.getRelationType() == RelationType.ASSOCIATION) {
                incomingAssociation.merge(relation.getTargetName(), 1L, Long::sum);
            }
        }

        Map<Integer, List<ClassInfo>> byLayer = classes.stream()
                .collect(Collectors.groupingBy(c -> layers.get(c.getSimpleName())));

        Map<String, Point2D> positions = new HashMap<>();
        int maxLayer = byLayer.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        double startX = MARGIN + LEGEND_WIDTH + 20;

        for (int layerIndex = 0; layerIndex <= maxLayer; layerIndex++) {
            List<ClassInfo> layerClasses = sortedLayerClasses(
                    byLayer.getOrDefault(layerIndex, List.of()),
                    incomingAssociation
            );
            double y = MARGIN + LARGE_LAYOUT_TOP_OFFSET + layerIndex * (estimateRowHeight() + V_GAP);

            for (int i = 0; i < layerClasses.size(); i++) {
                ClassInfo classInfo = layerClasses.get(i);
                double x = startX + i * (BOX_WIDTH + H_GAP);
                positions.put(classInfo.getSimpleName(), new Point2D(x, y));
            }
        }

        return positions;
    }

    private Map<String, Point2D> positionLargeProject(List<ClassInfo> classes, Map<String, Integer> layers) {
        Map<Integer, List<ClassInfo>> byLayer = classes.stream()
                .collect(Collectors.groupingBy(c -> layers.get(c.getSimpleName())));

        Map<String, Point2D> positions = new HashMap<>();
        List<Integer> orderedLayers = byLayer.keySet().stream().sorted().toList();
        double startX = MARGIN + LEGEND_WIDTH + 20;
        double rowStep = estimateRowHeight() + V_GAP;
        double y = MARGIN + LARGE_LAYOUT_TOP_OFFSET;

        for (int layerIndex : orderedLayers) {
            List<ClassInfo> layerClasses = sortedLayerClasses(byLayer.getOrDefault(layerIndex, List.of()), Map.of());
            if (layerClasses.isEmpty()) {
                continue;
            }

            int subRows = (layerClasses.size() + MAX_CLASSES_PER_ROW - 1) / MAX_CLASSES_PER_ROW;
            for (int i = 0; i < layerClasses.size(); i++) {
                int subRow = i / MAX_CLASSES_PER_ROW;
                int column = i % MAX_CLASSES_PER_ROW;
                ClassInfo classInfo = layerClasses.get(i);

                double x = startX + column * (BOX_WIDTH + H_GAP);
                positions.put(classInfo.getSimpleName(), new Point2D(x, y + subRow * rowStep));
            }

            y += subRows * rowStep + LARGE_LAYOUT_ROW_GAP;
        }

        return positions;
    }

    private List<ClassInfo> sortedLayerClasses(List<ClassInfo> layerClasses, Map<String, Long> incomingAssociation) {
        return layerClasses.stream()
                .sorted(Comparator
                        .comparing((ClassInfo c) -> kindOrder(c.getKind()))
                        .thenComparing(c -> incomingAssociation.getOrDefault(c.getSimpleName(), 0L))
                        .thenComparing(ClassInfo::getPackageName)
                        .thenComparing(ClassInfo::getSimpleName))
                .toList();
    }

    private int kindOrder(String kind) {
        return switch (kind) {
            case "interface" -> 0;
            case "enum" -> 1;
            case "abstract class", "abstract" -> 2;
            case "class" -> 3;
            default -> 4;
        };
    }

    private boolean isInterface(ClassInfo classInfo) {
        return "interface".equals(classInfo.getKind());
    }

    private double estimateRowHeight() {
        return 200;
    }

    private boolean isHierarchyRelation(RelationType type) {
        return type == RelationType.INHERITANCE || type == RelationType.IMPLEMENTATION;
    }

    private VBox createClassBox(ClassInfo classInfo) {
        boolean isInterface = "interface".equals(classInfo.getKind());

        VBox container = new VBox(4);
        container.setPrefWidth(BOX_WIDTH);
        if (isInterface) {
            container.setStyle(
                    "-fx-background-color: #e8f4fc; -fx-border-color: #2c6e9e; -fx-border-width: 1.5; -fx-padding: 8;"
            );
        } else {
            container.setStyle(
                    "-fx-background-color: #fafafa; -fx-border-color: #333; -fx-border-width: 1.5; -fx-padding: 8;"
            );
        }

        String stereotype = isInterface ? "interface" : classInfo.getKind();
        Label title = new Label("«" + stereotype + "» " + classInfo.getSimpleName());
        title.setWrapText(true);
        title.setMaxWidth(BOX_WIDTH - 16);
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a1a;" + (isInterface ? " -fx-font-style: italic;" : ""));
        container.getChildren().add(title);

        if (!classInfo.getPackageName().isEmpty()) {
            Label packageLabel = new Label(classInfo.getPackageName());
            packageLabel.setWrapText(true);
            packageLabel.setMaxWidth(BOX_WIDTH - 16);
            packageLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
            container.getChildren().add(packageLabel);
        }

        if (!classInfo.getFields().isEmpty()) {
            container.getChildren().add(sectionLabel("поля"));
            for (String field : classInfo.getFields()) {
                container.getChildren().add(memberLabel(field));
            }
        }

        if (!classInfo.getMethods().isEmpty()) {
            container.getChildren().add(sectionLabel("методы"));
            for (String method : classInfo.getMethods()) {
                container.getChildren().add(memberLabel(method));
            }
        }

        return container;
    }

    private Label sectionLabel(String title) {
        Label label = new Label("— " + title + " —");
        label.setStyle("-fx-text-fill: #888; -fx-font-size: 10; -fx-font-weight: bold;");
        return label;
    }

    private Label memberLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(BOX_WIDTH - 16);
        label.setStyle("-fx-font-size: 11; -fx-font-family: monospace;");
        return label;
    }

    private void assignConnectionSlots(List<RelationLayout> layouts, boolean forTarget) {
        Map<String, List<RelationLayout>> groups = new HashMap<>();
        for (RelationLayout layout : layouts) {
            String nodeName = forTarget
                    ? layout.relation().getTargetName()
                    : layout.relation().getSourceName();
            Side side = forTarget ? layout.targetSide() : layout.sourceSide();
            String key = nodeName + "|" + side;
            groups.computeIfAbsent(key, keyIgnored -> new ArrayList<>()).add(layout);
        }

        for (List<RelationLayout> group : groups.values()) {
            group.sort(Comparator.comparingDouble(layout -> connectionSortKey(layout, forTarget)));
            int count = group.size();
            for (int index = 0; index < count; index++) {
                RelationLayout layout = group.get(index);
                if (forTarget) {
                    layout.targetSlot = index;
                    layout.targetSlotCount = count;
                } else {
                    layout.sourceSlot = index;
                    layout.sourceSlotCount = count;
                }
            }
        }
    }

    private double connectionSortKey(RelationLayout layout, boolean forTarget) {
        Bounds anchor = forTarget ? layout.source() : layout.target();
        Side side = forTarget ? layout.targetSide() : layout.sourceSide();
        Point2D center = centerOf(anchor);
        return switch (side) {
            case TOP, BOTTOM -> center.getX();
            case LEFT, RIGHT -> center.getY();
        };
    }

    private SidePair chooseSides(Bounds source, Bounds target, RelationType relationType) {
        Point2D sourceCenter = centerOf(source);
        Point2D targetCenter = centerOf(target);
        double dx = targetCenter.getX() - sourceCenter.getX();
        double dy = targetCenter.getY() - sourceCenter.getY();

        if (relationType == RelationType.ASSOCIATION && Math.abs(dy) < 40) {
            return dx >= 0
                    ? new SidePair(Side.RIGHT, Side.LEFT)
                    : new SidePair(Side.LEFT, Side.RIGHT);
        }

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx >= 0
                    ? new SidePair(Side.RIGHT, Side.LEFT)
                    : new SidePair(Side.LEFT, Side.RIGHT);
        }

        return dy >= 0
                ? new SidePair(Side.BOTTOM, Side.TOP)
                : new SidePair(Side.TOP, Side.BOTTOM);
    }

    private void drawRelation(Group layer, RelationLayout layout) {
        AnchorPoints anchors = computeAnchorPoints(layout);
        RelationType relationType = layout.relation().getRelationType();
        Color color = colorFor(relationType);

        Line line = new Line(
                anchors.start().getX(), anchors.start().getY(),
                anchors.end().getX(), anchors.end().getY()
        );
        line.setStroke(color);
        line.setStrokeWidth(2.0);
        line.setStrokeLineCap(StrokeLineCap.ROUND);

        if (relationType == RelationType.IMPLEMENTATION) {
            line.getStrokeDashArray().addAll(10.0, 6.0);
        } else if (relationType == RelationType.ASSOCIATION) {
            line.getStrokeDashArray().addAll(6.0, 4.0);
        } else if (relationType == RelationType.DEPENDENCY) {
            line.getStrokeDashArray().addAll(3.0, 5.0);
        }

        Polygon arrowHead = createArrowHead(anchors.end(), anchors.arrowDirection(), relationType, color);
        Label edgeLabel = createEdgeLabel(anchors.labelPosition(), relationType);

        layer.getChildren().addAll(line, arrowHead, edgeLabel);
    }

    private AnchorPoints computeAnchorPoints(RelationLayout layout) {
        Point2D start = borderPoint(
                layout.source(),
                layout.sourceSide(),
                layout.sourceSlot(),
                layout.sourceSlotCount()
        );
        Point2D end = borderPoint(
                layout.target(),
                layout.targetSide(),
                layout.targetSlot(),
                layout.targetSlotCount()
        );
        Point2D direction = end.subtract(start);
        if (direction.magnitude() > 0.001) {
            direction = direction.normalize();
        } else {
            direction = new Point2D(1, 0);
        }

        return new AnchorPoints(start, end, direction, start.midpoint(end));
    }

    private Point2D borderPoint(Bounds bounds, Side side, int slotIndex, int slotCount) {
        double cx = bounds.getMinX() + bounds.getWidth() / 2.0;
        double cy = bounds.getMinY() + bounds.getHeight() / 2.0;
        double alongOffset = slotAlongOffset(bounds, side, slotIndex, slotCount);
        return switch (side) {
            case TOP -> new Point2D(cx + alongOffset, bounds.getMinY());
            case BOTTOM -> new Point2D(cx + alongOffset, bounds.getMaxY());
            case LEFT -> new Point2D(bounds.getMinX(), cy + alongOffset);
            case RIGHT -> new Point2D(bounds.getMaxX(), cy + alongOffset);
        };
    }

    private double slotAlongOffset(Bounds bounds, Side side, int slotIndex, int slotCount) {
        if (slotCount <= 1) {
            return 0;
        }

        double span = switch (side) {
            case TOP, BOTTOM -> bounds.getWidth() - 2 * EDGE_BORDER_MARGIN;
            case LEFT, RIGHT -> bounds.getHeight() - 2 * EDGE_BORDER_MARGIN;
        };
        double step = Math.min(EDGE_SLOT_SPACING, span / Math.max(1, slotCount - 1));
        return (slotIndex - (slotCount - 1) / 2.0) * step;
    }

    private Point2D centerOf(Bounds bounds) {
        return new Point2D(bounds.getMinX() + bounds.getWidth() / 2.0, bounds.getMinY() + bounds.getHeight() / 2.0);
    }

    private Polygon createArrowHead(Point2D tip, Point2D direction, RelationType relationType, Color color) {
        double arrowLength = 14;
        double arrowWidth = 9;
        Point2D unit = direction.normalize();
        Point2D base = tip.subtract(unit.multiply(arrowLength));

        Point2D left = base.add(new Point2D(-unit.getY(), unit.getX()).multiply(arrowWidth / 2.0));
        Point2D right = base.add(new Point2D(unit.getY(), -unit.getX()).multiply(arrowWidth / 2.0));

        Polygon arrow = new Polygon(tip.getX(), tip.getY(), left.getX(), left.getY(), right.getX(), right.getY());
        arrow.setStroke(color);
        arrow.setStrokeWidth(1.5);
        if (relationType == RelationType.ASSOCIATION) {
            arrow.setFill(color);
        } else {
            arrow.setFill(Color.WHITE);
        }
        return arrow;
    }

    private Label createEdgeLabel(Point2D position, RelationType relationType) {
        Label label = new Label(labelFor(relationType));
        label.setStyle(
                "-fx-background-color: rgba(255,255,255,0.92);"
                        + " -fx-padding: 2 6 2 6;"
                        + " -fx-font-size: 10;"
                        + " -fx-font-weight: bold;"
                        + " -fx-text-fill: " + toHex(colorFor(relationType)) + ";"
                        + " -fx-border-color: " + toHex(colorFor(relationType)) + ";"
                        + " -fx-border-radius: 4; -fx-background-radius: 4;"
        );
        label.applyCss();
        label.layout();
        label.relocate(position.getX() - label.prefWidth(-1) / 2.0, position.getY() - label.prefHeight(-1) / 2.0);
        return label;
    }

    private String labelFor(RelationType relationType) {
        return switch (relationType) {
            case INHERITANCE -> "наследует";
            case IMPLEMENTATION -> "реализует";
            case ASSOCIATION -> "использует";
            case DEPENDENCY -> "зависит от";
        };
    }

    private Color colorFor(RelationType relationType) {
        return switch (relationType) {
            case INHERITANCE -> COLOR_INHERITANCE;
            case IMPLEMENTATION -> COLOR_IMPLEMENTATION;
            case ASSOCIATION -> COLOR_ASSOCIATION;
            case DEPENDENCY -> COLOR_DEPENDENCY;
        };
    }

    private VBox createLegend() {
        VBox legend = new VBox(6);
        legend.setPrefWidth(LEGEND_WIDTH);
        legend.setStyle(
                "-fx-background-color: rgba(255,255,255,0.95);"
                        + " -fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 10; -fx-background-radius: 6;"
        );

        Label title = new Label("Условные обозначения");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        legend.getChildren().add(title);

        legend.getChildren().add(legendRow("наследует", COLOR_INHERITANCE, false));
        legend.getChildren().add(legendRow("реализует", COLOR_IMPLEMENTATION, true));
        legend.getChildren().add(legendRow("использует", COLOR_ASSOCIATION, true));
        legend.getChildren().add(legendRow("зависит от", COLOR_DEPENDENCY, true));

        Label hint = new Label("Стрелка указывает на цель:\nот класса → к интерфейсу / родителю / зависимости");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #555; -fx-font-size: 10;");
        legend.getChildren().add(hint);

        return legend;
    }

    private HBoxLegend legendRow(String text, Color color, boolean dashed) {
        return new HBoxLegend(text, color, dashed);
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private double computeMaxX(Map<String, Bounds> boundsMap) {
        return boundsMap.values().stream().mapToDouble(Bounds::getMaxX).max().orElse(900);
    }

    private double computeMaxY(Map<String, Bounds> boundsMap) {
        return boundsMap.values().stream().mapToDouble(Bounds::getMaxY).max().orElse(700);
    }

    private enum Side {
        TOP, BOTTOM, LEFT, RIGHT
    }

    private record SidePair(Side sourceSide, Side targetSide) {
    }

    private static final class RelationLayout {
        private final RelationInfo relation;
        private final Bounds source;
        private final Bounds target;
        private final Side sourceSide;
        private final Side targetSide;
        private int sourceSlot;
        private int sourceSlotCount = 1;
        private int targetSlot;
        private int targetSlotCount = 1;

        private RelationLayout(
                RelationInfo relation,
                Bounds source,
                Bounds target,
                Side sourceSide,
                Side targetSide
        ) {
            this.relation = relation;
            this.source = source;
            this.target = target;
            this.sourceSide = sourceSide;
            this.targetSide = targetSide;
        }

        private RelationInfo relation() {
            return relation;
        }

        private Bounds source() {
            return source;
        }

        private Bounds target() {
            return target;
        }

        private Side sourceSide() {
            return sourceSide;
        }

        private Side targetSide() {
            return targetSide;
        }

        private int sourceSlot() {
            return sourceSlot;
        }

        private int sourceSlotCount() {
            return sourceSlotCount;
        }

        private int targetSlot() {
            return targetSlot;
        }

        private int targetSlotCount() {
            return targetSlotCount;
        }
    }

    private record AnchorPoints(
            Point2D start,
            Point2D end,
            Point2D arrowDirection,
            Point2D labelPosition
    ) {
    }

    private static class HBoxLegend extends Pane {
        HBoxLegend(String text, Color color, boolean dashed) {
            Line sample = new Line(0, 8, 36, 8);
            sample.setStroke(color);
            sample.setStrokeWidth(2);
            if (dashed) {
                sample.getStrokeDashArray().addAll(8.0, 5.0);
            }

            Polygon arrow = new Polygon(36, 8, 28, 4, 28, 12);
            arrow.setStroke(color);
            arrow.setFill(color);

            Label label = new Label(text + "  —  от источника к цели");
            label.setLayoutX(44);
            label.setLayoutY(0);
            label.setStyle("-fx-font-size: 11;");

            getChildren().addAll(sample, arrow, label);
            setPrefHeight(20);
            setPrefWidth(LEGEND_WIDTH - 20);
        }
    }
}
