package org.example.javakyrsach2;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.javakyrsach2.db.DatabaseInitializer;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        boolean databaseReady = initializeDatabase();
        MainView mainView = new MainView(databaseReady);
        Scene scene = new Scene(mainView, 1200, 800);
        scene.getStylesheets().add(
                HelloApplication.class.getResource("/app.css").toExternalForm()
        );
        stage.setTitle("Анализ архитектуры проекта");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.show();
    }

    private boolean initializeDatabase() {
        try {
            DatabaseInitializer.initialize();
            return DatabaseInitializer.testConnection();
        } catch (Exception exception) {
            System.err.println("База данных недоступна: " + exception.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}