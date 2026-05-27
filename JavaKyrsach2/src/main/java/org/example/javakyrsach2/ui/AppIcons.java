package org.example.javakyrsach2.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public final class AppIcons {
    private AppIcons() {
    }

    public static Node folder(double size, Color color) {
        SVGPath path = new SVGPath();
        path.setContent("M 3 5 L 9 5 L 11 7 L 21 7 L 21 19 L 3 19 Z");
        return scale(path, size, color, 1.5);
    }

    public static Node chart(double size, Color color) {
        SVGPath path = new SVGPath();
        path.setContent("M 3 18 L 9 12 L 13 15 L 21 6");
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return scale(path, size, color, 2.0);
    }

    private static Node scale(SVGPath path, double size, Color color, double strokeWidth) {
        path.setFill(Color.TRANSPARENT);
        path.setStroke(color);
        path.setStrokeWidth(strokeWidth);

        double scale = size / 24.0;
        Group group = new Group(path);
        group.setScaleX(scale);
        group.setScaleY(scale);
        return group;
    }
}
