package org.example.javakyrsach2.export;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import org.example.javakyrsach2.ui.DiagramPane;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;

public final class DiagramPngExporter {
    private static final double EXPORT_SCALE = 2.0;

    private DiagramPngExporter() {
    }

    public static void export(DiagramPane pane, Path path) throws IOException {
        double width = pane.getPrefWidth();
        double height = pane.getPrefHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Диаграмма не построена или имеет нулевой размер.");
        }

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.web("#ececec"));
        parameters.setViewport(new Rectangle2D(0, 0, width, height));
        parameters.setTransform(new Scale(EXPORT_SCALE, EXPORT_SCALE));

        int imageWidth = (int) Math.ceil(width * EXPORT_SCALE);
        int imageHeight = (int) Math.ceil(height * EXPORT_SCALE);
        WritableImage image = new WritableImage(imageWidth, imageHeight);
        pane.snapshot(parameters, image);

        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", path.toFile());
    }
}
