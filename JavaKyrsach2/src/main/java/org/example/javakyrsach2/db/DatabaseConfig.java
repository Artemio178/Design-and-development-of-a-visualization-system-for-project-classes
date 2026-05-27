package org.example.javakyrsach2.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {
    private static final Properties PROPERTIES = loadProperties();

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("MySQL JDBC драйвер не найден в classpath.", exception);
        }
    }

    private DatabaseConfig() {
    }

    public static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                property("db.url", "spring.datasource.backup.url"),
                property("db.username", "spring.datasource.backup.username"),
                property("db.password", "spring.datasource.backup.password")
        );
    }

    public static boolean isConfigured() {
        return property("db.url", "spring.datasource.backup.url") != null
                && property("db.username", "spring.datasource.backup.username") != null
                && property("db.password", "spring.datasource.backup.password") != null;
    }

    private static String property(String primaryKey, String fallbackKey) {
        String value = PROPERTIES.getProperty(primaryKey);
        if (value == null || value.isBlank()) {
            value = PROPERTIES.getProperty(fallbackKey);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream("/db.properties")) {
            if (input == null) {
                return properties;
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать db.properties", exception);
        }
        return properties;
    }
}
