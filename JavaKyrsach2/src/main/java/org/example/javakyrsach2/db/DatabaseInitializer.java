package org.example.javakyrsach2.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void initialize() throws SQLException {
        if (!DatabaseConfig.isConfigured()) {
            throw new SQLException("Файл db.properties не настроен.");
        }

        try (Connection connection = DatabaseConfig.openConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : readSchemaStatements()) {
                statement.execute(sql);
            }
        }
    }

    public static boolean testConnection() {
        if (!DatabaseConfig.isConfigured()) {
            return false;
        }
        try (Connection connection = DatabaseConfig.openConnection()) {
            return connection.isValid(3);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static List<String> readSchemaStatements() throws SQLException {
        try (InputStream input = DatabaseInitializer.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new SQLException("Не найден файл schema.sql");
            }

            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    current.append(line).append('\n');
                    if (trimmed.endsWith(";")) {
                        statements.add(current.toString().trim());
                        current.setLength(0);
                    }
                }
            }

            if (!current.isEmpty()) {
                statements.add(current.toString().trim());
            }
            return statements;
        } catch (IOException exception) {
            throw new SQLException("Не удалось прочитать schema.sql", exception);
        }
    }
}
