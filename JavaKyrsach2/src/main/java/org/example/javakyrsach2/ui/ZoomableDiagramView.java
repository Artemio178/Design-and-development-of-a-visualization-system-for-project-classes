package org.example.javakyrsach2.ui;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;

public class ZoomableDiagramView extends BorderPane {
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 4.0;
    private static final double ZOOM_FACTOR = 1.15;
    private static final double PAN_PADDING = 120;

    private final DiagramPane diagramPane = new DiagramPane();
    private final Group contentGroup = new Group(diagramPane);
    private final Pane scrollContent = new Pane(contentGroup);
    private final ScrollPane scrollPane = new ScrollPane(scrollContent);
    private final Label zoomLabel = new Label("100%");

    private double scale = 1.0;
    private boolean panning;
    private double panStartScreenX;
    private double panStartScreenY;
    private double panStartHvalue;
    private double panStartVvalue;

    public ZoomableDiagramView() {
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scrollPane.getStyleClass().add("scroll-pane-diagram");
        scrollContent.setStyle("-fx-background-color: #f5f7fa;");
        contentGroup.setAutoSizeChildren(true);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScroll);
        scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handlePanStart);
        scrollPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handlePanDrag);
        scrollPane.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handlePanEnd);

        diagramPane.widthProperty().addListener((obs, oldVal, newVal) -> updateScrollContentSize());
        diagramPane.heightProperty().addListener((obs, oldVal, newVal) -> updateScrollContentSize());

        setCenter(scrollPane);
    }

    public DiagramPane getDiagramPane() {
        return diagramPane;
    }

    public Label getZoomLabel() {
        return zoomLabel;
    }

    public void zoomIn() {
        zoomAtViewportCenter(scale * ZOOM_FACTOR);
    }

    public void zoomOut() {
        zoomAtViewportCenter(scale / ZOOM_FACTOR);
    }

    private void zoomAtViewportCenter(double newScale) {
        Bounds viewport = scrollPane.getViewportBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
            applyScale(newScale);
            return;
        }
        zoomAt(viewport.getWidth() / 2.0, viewport.getHeight() / 2.0, newScale);
    }

    public void resetZoom() {
        applyScale(1.0);
        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);
    }

    public void fitToViewport() {
        Bounds content = diagramPane.getLayoutBounds();
        if (content.getWidth() <= 0 || content.getHeight() <= 0) {
            return;
        }

        Bounds viewport = scrollPane.getViewportBounds();
        double viewportWidth = viewport.getWidth();
        double viewportHeight = viewport.getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        double fitScale = Math.min(
                viewportWidth / content.getWidth(),
                viewportHeight / content.getHeight()
        ) * 0.95;

        applyScale(clamp(fitScale, MIN_SCALE, MAX_SCALE));
        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);
    }

    public void fitIfLargerThanViewport() {
        Bounds content = diagramPane.getLayoutBounds();
        Bounds viewport = scrollPane.getViewportBounds();
        if (viewportWidthExceeds(content, viewport) || viewportHeightExceeds(content, viewport)) {
            fitToViewport();
        }
    }

    public void refreshAfterModelUpdate() {
        diagramPane.applyCss();
        diagramPane.layout();
        updateScrollContentSize();
        javafx.application.Platform.runLater(this::fitIfLargerThanViewport);
    }

    private boolean viewportWidthExceeds(Bounds content, Bounds viewport) {
        return content.getWidth() > viewport.getWidth();
    }

    private boolean viewportHeightExceeds(Bounds content, Bounds viewport) {
        return content.getHeight() > viewport.getHeight();
    }

    private void handleScroll(ScrollEvent event) {
        if (event.isControlDown()) {
            event.consume();
            double factor = event.getDeltaY() > 0 ? ZOOM_FACTOR : 1.0 / ZOOM_FACTOR;
            double newScale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
            zoomAt(event.getX(), event.getY(), newScale);
            return;
        }

        double deltaY = event.getDeltaY();
        double deltaX = event.getDeltaX();
        if (event.isShiftDown() && Math.abs(deltaY) > Math.abs(deltaX)) {
            deltaX = deltaY;
            deltaY = 0;
        }

        if (!scrollByPixels(-deltaX, -deltaY)) {
            return;
        }
        event.consume();
    }

    private void handlePanStart(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || event.isControlDown()) {
            return;
        }
        panning = true;
        panStartScreenX = event.getScreenX();
        panStartScreenY = event.getScreenY();
        panStartHvalue = scrollPane.getHvalue();
        panStartVvalue = scrollPane.getVvalue();
        event.consume();
    }

    private void handlePanDrag(MouseEvent event) {
        if (!panning) {
            return;
        }
        double dx = event.getScreenX() - panStartScreenX;
        double dy = event.getScreenY() - panStartScreenY;

        Bounds viewport = scrollPane.getViewportBounds();
        double scrollableWidth = Math.max(0, scrollContent.getWidth() - viewport.getWidth());
        double scrollableHeight = Math.max(0, scrollContent.getHeight() - viewport.getHeight());

        if (scrollableWidth > 0) {
            scrollPane.setHvalue(clamp(panStartHvalue - dx / scrollableWidth, 0, 1));
        }
        if (scrollableHeight > 0) {
            scrollPane.setVvalue(clamp(panStartVvalue - dy / scrollableHeight, 0, 1));
        }
        event.consume();
    }

    private void handlePanEnd(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            panning = false;
        }
    }

    private boolean scrollByPixels(double deltaX, double deltaY) {
        Bounds viewport = scrollPane.getViewportBounds();
        double scrollableWidth = Math.max(0, scrollContent.getWidth() - viewport.getWidth());
        double scrollableHeight = Math.max(0, scrollContent.getHeight() - viewport.getHeight());

        boolean moved = false;
        if (scrollableWidth > 0 && Math.abs(deltaX) > 0.01) {
            scrollPane.setHvalue(clamp(
                    scrollPane.getHvalue() - deltaX / scrollableWidth,
                    0,
                    1
            ));
            moved = true;
        }
        if (scrollableHeight > 0 && Math.abs(deltaY) > 0.01) {
            scrollPane.setVvalue(clamp(
                    scrollPane.getVvalue() - deltaY / scrollableHeight,
                    0,
                    1
            ));
            moved = true;
        }
        return moved;
    }

    private void zoomAt(double pivotX, double pivotY, double newScale) {
        double oldScale = scale;
        newScale = clamp(newScale, MIN_SCALE, MAX_SCALE);
        if (Math.abs(newScale - oldScale) < 0.001) {
            return;
        }

        Bounds viewport = scrollPane.getViewportBounds();
        double oldContentWidth = contentWidthAtScale(oldScale);
        double oldContentHeight = contentHeightAtScale(oldScale);

        double scrollableWidth = Math.max(0, oldContentWidth - viewport.getWidth());
        double scrollableHeight = Math.max(0, oldContentHeight - viewport.getHeight());

        double contentX = scrollPane.getHvalue() * scrollableWidth + pivotX;
        double contentY = scrollPane.getVvalue() * scrollableHeight + pivotY;

        applyScale(newScale);

        double newContentWidth = contentWidthAtScale(scale);
        double newContentHeight = contentHeightAtScale(scale);
        double newScrollableWidth = Math.max(0, newContentWidth - viewport.getWidth());
        double newScrollableHeight = Math.max(0, newContentHeight - viewport.getHeight());

        double ratio = newScale / oldScale;
        double newContentX = contentX * ratio;
        double newContentY = contentY * ratio;

        if (newScrollableWidth > 0) {
            scrollPane.setHvalue(clamp((newContentX - pivotX) / newScrollableWidth, 0, 1));
        } else {
            scrollPane.setHvalue(0);
        }

        if (newScrollableHeight > 0) {
            scrollPane.setVvalue(clamp((newContentY - pivotY) / newScrollableHeight, 0, 1));
        } else {
            scrollPane.setVvalue(0);
        }
    }

    private void applyScale(double newScale) {
        scale = clamp(newScale, MIN_SCALE, MAX_SCALE);
        contentGroup.getTransforms().setAll(new Scale(scale, scale, 0, 0));
        updateScrollContentSize();
        zoomLabel.setText(Math.round(scale * 100) + "%");
    }

    private double contentWidthAtScale(double scaleValue) {
        return diagramPane.getPrefWidth() * scaleValue + PAN_PADDING;
    }

    private double contentHeightAtScale(double scaleValue) {
        return diagramPane.getPrefHeight() * scaleValue + PAN_PADDING;
    }

    private void updateScrollContentSize() {
        scrollContent.setMinSize(contentWidthAtScale(scale), contentHeightAtScale(scale));
        scrollContent.setPrefSize(contentWidthAtScale(scale), contentHeightAtScale(scale));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
