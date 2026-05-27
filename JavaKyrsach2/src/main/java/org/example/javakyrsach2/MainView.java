package org.example.javakyrsach2;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.javakyrsach2.db.AnalysisSummary;
import org.example.javakyrsach2.db.DiagramRepository;
import org.example.javakyrsach2.model.DiagramModel;
import org.example.javakyrsach2.service.JavaClassParser;
import org.example.javakyrsach2.service.ProjectScanner;
import org.example.javakyrsach2.ui.AppIcons;
import org.example.javakyrsach2.ui.AppTheme;
import org.example.javakyrsach2.ui.ZoomableDiagramView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class MainView extends BorderPane {
    private static final double CARD_MAX_WIDTH = 720;

    private final TextField pathField = new TextField();
    private final Label statusLabel = new Label();
    private final ZoomableDiagramView diagramView = new ZoomableDiagramView();

    private final ProjectScanner projectScanner = new ProjectScanner();
    private final JavaClassParser classParser = new JavaClassParser();
    private final DiagramRepository diagramRepository = new DiagramRepository();

    private final boolean databaseReady;
    private Long lastAnalysisId;

    public MainView(boolean databaseReady) {
        this.databaseReady = databaseReady;
        getStyleClass().add("root-pane");

        setTop(buildTopSection());
        setCenter(buildDiagramSection());
        setBottom(buildStatusBar());
        BorderPane.setMargin(getCenter(), new Insets(0, 24, 16, 24));
        BorderPane.setMargin(getBottom(), new Insets(0, 24, 16, 24));

        if (databaseReady) {
            statusLabel.setText("БД подключена. Выберите проект и постройте диаграмму.");
        } else {
            statusLabel.setText("БД недоступна — работа только с файлами проекта. Проверьте db.properties и MySQL.");
        }
    }

    private VBox buildTopSection() {
        VBox top = new VBox(16);
        top.setPadding(new Insets(24, 24, 0, 24));
        top.setAlignment(Pos.TOP_CENTER);
        top.getChildren().add(wrapCard(buildSetupCard()));
        return top;
    }

    private Region wrapCard(VBox card) {
        card.setMaxWidth(CARD_MAX_WIDTH);
        card.setEffect(AppTheme.cardShadow());
        return card;
    }

    private VBox buildSetupCard() {
        VBox card = new VBox(20);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28, 32, 32, 32));

        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(AppIcons.folder(28, Color.web("#1a1a1a")));

        VBox titles = new VBox(4);
        Label title = new Label("Анализ архитектуры проекта");
        title.getStyleClass().add("card-title");
        Label subtitle = new Label(
                "Приложение проанализирует структуру проекта и построит диаграмму архитектуры"
        );
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setMaxWidth(CARD_MAX_WIDTH - 100);
        titles.getChildren().addAll(title, subtitle);
        header.getChildren().add(titles);

        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        pathField.setPromptText("/path/to/your/project");
        pathField.getStyleClass().add("path-field");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseButton = createBrowseButton();
        inputRow.getChildren().addAll(pathField, browseButton);

        Button buildButton = createBuildButton();
        buildButton.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(header, inputRow, buildButton);
        return card;
    }

    private Button createBrowseButton() {
        Button browseButton = new Button("Обзор");
        browseButton.getStyleClass().add("button-browse");
        browseButton.setGraphic(AppIcons.folder(16, Color.web("#555555")));
        browseButton.setOnAction(event -> chooseDirectory(getScene() != null ? getScene().getWindow() : null));
        return browseButton;
    }

    private Button createBuildButton() {
        Button buildButton = new Button("Построить диаграмму");
        buildButton.getStyleClass().add("button-primary");
        buildButton.setGraphic(AppIcons.chart(18, Color.WHITE));
        buildButton.setMaxWidth(Double.MAX_VALUE);
        buildButton.setOnAction(event -> buildDiagram());
        return buildButton;
    }

    private VBox buildDiagramSection() {
        VBox diagramCard = new VBox(12);
        diagramCard.getStyleClass().addAll("card", "diagram-card");
        diagramCard.setPadding(new Insets(16));
        diagramCard.setEffect(AppTheme.cardShadow());
        diagramCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(diagramView, Priority.ALWAYS);

        diagramCard.getChildren().addAll(buildDiagramToolbar(), diagramView);
        return diagramCard;
    }

    private HBox buildDiagramToolbar() {
        HBox toolbar = new HBox(6);
        toolbar.getStyleClass().add("diagram-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button zoomOutButton = iconButton("−", "Уменьшить");
        zoomOutButton.setOnAction(event -> diagramView.zoomOut());

        Label zoomLabel = diagramView.getZoomLabel();
        zoomLabel.getStyleClass().add("zoom-label");

        Button zoomInButton = iconButton("+", "Увеличить");
        zoomInButton.setOnAction(event -> diagramView.zoomIn());

        Button resetZoomButton = secondaryButton("100%");
        resetZoomButton.setOnAction(event -> diagramView.resetZoom());

        Button fitButton = secondaryButton("Вписать");
        fitButton.setOnAction(event -> diagramView.fitToViewport());

        Button exportPngButton = secondaryButton("PNG");
        exportPngButton.setOnAction(event -> exportDiagram(false));

        Button exportSvgButton = secondaryButton("SVG");
        exportSvgButton.setOnAction(event -> exportDiagram(true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                zoomOutButton,
                zoomLabel,
                zoomInButton,
                resetZoomButton,
                fitButton,
                exportPngButton,
                exportSvgButton,
                spacer
        );

        if (databaseReady) {
            Button loadDbButton = secondaryButton("Из БД");
            loadDbButton.setOnAction(event -> loadFromDatabase());

            Button searchDbButton = secondaryButton("Поиск");
            searchDbButton.setOnAction(event -> searchInDatabase());

            toolbar.getChildren().addAll(loadDbButton, searchDbButton);
        }

        return toolbar;
    }

    private Button iconButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("button-icon");
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("button-secondary");
        return button;
    }

    private HBox buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.setPadding(new Insets(0, 8, 0, 8));
        return statusBar;
    }

    private void chooseDirectory(Window ownerWindow) {
        if (ownerWindow == null) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку проекта");
        File selectedDir = chooser.showDialog(ownerWindow);
        if (selectedDir != null) {
            pathField.setText(selectedDir.toPath().toString());
        }
    }

    private void buildDiagram() {
        String pathText = pathField.getText().trim();
        if (pathText.isEmpty()) {
            showWarning("Нужно указать путь к проекту.");
            return;
        }

        Path root = Path.of(pathText);
        statusLabel.setText("Анализ проекта...");

        Thread worker = new Thread(() -> {
            try {
                List<Path> javaFiles = projectScanner.findJavaFiles(root);
                DiagramModel model = classParser.parse(javaFiles);

                Long analysisId = null;
                if (databaseReady) {
                    try {
                        analysisId = diagramRepository.save(root, model);
                    } catch (SQLException sqlException) {
                        Platform.runLater(() -> showError(
                                "Диаграмма построена, но не сохранена в БД: " + sqlException.getMessage()
                        ));
                    }
                }

                Long savedAnalysisId = analysisId;
                Platform.runLater(() -> {
                    lastAnalysisId = savedAnalysisId;
                    diagramView.getDiagramPane().setModel(model);
                    diagramView.refreshAfterModelUpdate();
                    String message = "Готово. Классов: " + model.getClasses().size()
                            + ", связей: " + model.getRelations().size();
                    if (savedAnalysisId != null) {
                        message += ". Сохранено в БД (анализ #" + savedAnalysisId + ").";
                    }
                    message += " Масштаб: Ctrl + колёсико.";
                    statusLabel.setText(message);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка при чтении проекта.");
                    showError("Не удалось прочитать проект: " + e.getMessage());
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка при анализе.");
                    showError("Ошибка анализа: " + e.getMessage());
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void loadFromDatabase() {
        if (!databaseReady) {
            showWarning("База данных не подключена.");
            return;
        }

        String pathText = pathField.getText().trim();
        Thread worker = new Thread(() -> {
            try {
                List<AnalysisSummary> summaries = pathText.isEmpty()
                        ? diagramRepository.findRecent(20)
                        : diagramRepository.findRecentByPath(Path.of(pathText), 20);

                Platform.runLater(() -> {
                    if (summaries.isEmpty()) {
                        showWarning("В базе нет сохранённых анализов для выбранного пути.");
                        return;
                    }

                    ChoiceDialog<AnalysisSummary> dialog = new ChoiceDialog<>(summaries.get(0), summaries);
                    dialog.setTitle("Загрузка из БД");
                    dialog.setHeaderText("Выберите сохранённый анализ");
                    dialog.setContentText("Анализ:");

                    Optional<AnalysisSummary> selected = dialog.showAndWait();
                    selected.ifPresent(summary -> loadAnalysisFromDatabase(summary.id(), summary.projectPath()));
                });
            } catch (SQLException exception) {
                Platform.runLater(() -> showError("Ошибка чтения из БД: " + exception.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void loadAnalysisFromDatabase(long analysisId, String projectPath) {
        statusLabel.setText("Загрузка анализа #" + analysisId + " из БД...");
        Thread worker = new Thread(() -> {
            try {
                DiagramModel model = diagramRepository.load(analysisId);
                Platform.runLater(() -> {
                    lastAnalysisId = analysisId;
                    if (projectPath != null && !projectPath.isBlank()) {
                        pathField.setText(projectPath);
                    }
                    diagramView.getDiagramPane().setModel(model);
                    diagramView.refreshAfterModelUpdate();
                    statusLabel.setText("Загружено из БД (анализ #" + analysisId + "). Классов: "
                            + model.getClasses().size() + ", связей: " + model.getRelations().size());
                });
            } catch (SQLException exception) {
                Platform.runLater(() -> showError("Не удалось загрузить анализ: " + exception.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void searchInDatabase() {
        if (!databaseReady) {
            showWarning("База данных не подключена.");
            return;
        }
        if (lastAnalysisId == null) {
            showWarning("Сначала постройте или загрузите диаграмму — нужен сохранённый анализ.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Поиск в БД");
        dialog.setHeaderText("Поиск класса по имени");
        dialog.setContentText("Фрагмент имени:");

        Optional<String> query = dialog.showAndWait();
        if (query.isEmpty() || query.get().isBlank()) {
            return;
        }

        long analysisId = lastAnalysisId;
        String namePart = query.get().trim();
        Thread worker = new Thread(() -> {
            try {
                List<String> classNames = diagramRepository.findClassNames(analysisId, namePart);
                Platform.runLater(() -> {
                    if (classNames.isEmpty()) {
                        showWarning("Классы с именем, содержащим \"" + namePart + "\", не найдены.");
                        return;
                    }
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Результаты поиска");
                    alert.setHeaderText("Найдено классов: " + classNames.size());
                    alert.setContentText(String.join("\n", classNames));
                    alert.showAndWait();
                });
            } catch (SQLException exception) {
                Platform.runLater(() -> showError("Ошибка поиска: " + exception.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void exportDiagram(boolean svg) {
        if (!diagramView.getDiagramPane().canExport()) {
            showWarning("Сначала постройте диаграмму.");
            return;
        }

        Window ownerWindow = getScene() != null ? getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(svg ? "Сохранить диаграмму в SVG" : "Сохранить диаграмму в PNG");
        if (svg) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG (*.svg)", "*.svg"));
            chooser.setInitialFileName("diagram.svg");
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG (*.png)", "*.png"));
            chooser.setInitialFileName("diagram.png");
        }

        File selectedFile = chooser.showSaveDialog(ownerWindow);
        if (selectedFile == null) {
            return;
        }

        File outputFile = ensureExtension(selectedFile, svg ? ".svg" : ".png");
        try {
            if (svg) {
                diagramView.getDiagramPane().exportToSvg(outputFile.toPath());
            } else {
                diagramView.getDiagramPane().exportToPng(outputFile.toPath());
            }
            statusLabel.setText("Диаграмма сохранена: " + outputFile.getAbsolutePath());
        } catch (IOException | IllegalStateException e) {
            showError("Не удалось сохранить файл: " + e.getMessage());
        }
    }

    private File ensureExtension(File file, String extension) {
        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(extension)) {
            return file;
        }
        return new File(file.getParentFile(), file.getName() + extension);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText("Некорректный ввод");
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }
}
