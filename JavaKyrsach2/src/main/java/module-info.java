module org.example.javakyrsach2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires java.sql;
    requires com.github.javaparser.core;

    requires org.kordamp.bootstrapfx.core;

    opens org.example.javakyrsach2 to javafx.fxml;
    opens org.example.javakyrsach2.model to javafx.base;
    exports org.example.javakyrsach2;
}