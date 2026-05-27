package org.example.javakyrsach2.db;

import org.example.javakyrsach2.model.ClassInfo;
import org.example.javakyrsach2.model.DiagramModel;
import org.example.javakyrsach2.model.RelationInfo;
import org.example.javakyrsach2.model.RelationType;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiagramRepository {

    public long save(Path projectPath, DiagramModel model) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long analysisId = insertAnalysis(connection, projectPath, model);
                Map<String, Long> classIds = insertClasses(connection, analysisId, model);
                insertMembers(connection, model, classIds);
                insertRelations(connection, analysisId, model);
                connection.commit();
                return analysisId;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public DiagramModel load(long analysisId) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            List<ClassInfo> classes = loadClasses(connection, analysisId);
            List<RelationInfo> relations = loadRelations(connection, analysisId);
            return new DiagramModel(classes, relations);
        }
    }

    public List<AnalysisSummary> findRecentByPath(Path projectPath, int limit) throws SQLException {
        String sql = """
                SELECT id, project_path, class_count, relation_count, created_at
                FROM analysis_run
                WHERE project_path = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectPath.toString());
            statement.setInt(2, limit);

            List<AnalysisSummary> result = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapAnalysisSummary(resultSet));
                }
            }
            return result;
        }
    }

    public List<AnalysisSummary> findRecent(int limit) throws SQLException {
        String sql = """
                SELECT id, project_path, class_count, relation_count, created_at
                FROM analysis_run
                ORDER BY created_at DESC
                LIMIT ?
                """;

        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            List<AnalysisSummary> result = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapAnalysisSummary(resultSet));
                }
            }
            return result;
        }
    }

    public List<String> findClassNames(long analysisId, String namePart) throws SQLException {
        String sql = """
                SELECT simple_name
                FROM diagram_class
                WHERE analysis_id = ? AND simple_name LIKE ?
                ORDER BY simple_name
                """;

        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, analysisId);
            statement.setString(2, "%" + namePart + "%");

            List<String> names = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("simple_name"));
                }
            }
            return names;
        }
    }

    private long insertAnalysis(Connection connection, Path projectPath, DiagramModel model) throws SQLException {
        String sql = """
                INSERT INTO analysis_run (project_path, class_count, relation_count)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, projectPath.toString());
            statement.setInt(2, model.getClasses().size());
            statement.setInt(3, model.getRelations().size());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Не удалось получить id анализа.");
    }

    private Map<String, Long> insertClasses(Connection connection, long analysisId, DiagramModel model) throws SQLException {
        String sql = """
                INSERT INTO diagram_class (analysis_id, simple_name, package_name, kind)
                VALUES (?, ?, ?, ?)
                """;

        Map<String, Long> classIds = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (ClassInfo classInfo : model.getClasses()) {
                statement.setLong(1, analysisId);
                statement.setString(2, classInfo.getSimpleName());
                statement.setString(3, classInfo.getPackageName());
                statement.setString(4, classInfo.getKind());
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        classIds.put(classInfo.getSimpleName(), keys.getLong(1));
                    }
                }
            }
        }
        return classIds;
    }

    private void insertMembers(Connection connection, DiagramModel model, Map<String, Long> classIds) throws SQLException {
        String sql = """
                INSERT INTO diagram_class_member (class_id, member_type, signature, sort_order)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ClassInfo classInfo : model.getClasses()) {
                Long classId = classIds.get(classInfo.getSimpleName());
                if (classId == null) {
                    continue;
                }

                int order = 0;
                for (String field : classInfo.getFields()) {
                    statement.setLong(1, classId);
                    statement.setString(2, "field");
                    statement.setString(3, field);
                    statement.setInt(4, order++);
                    statement.addBatch();
                }
                for (String method : classInfo.getMethods()) {
                    statement.setLong(1, classId);
                    statement.setString(2, "method");
                    statement.setString(3, method);
                    statement.setInt(4, order++);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void insertRelations(Connection connection, long analysisId, DiagramModel model) throws SQLException {
        String sql = """
                INSERT INTO diagram_relation (analysis_id, source_name, target_name, relation_type)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (RelationInfo relation : model.getRelations()) {
                statement.setLong(1, analysisId);
                statement.setString(2, relation.getSourceName());
                statement.setString(3, relation.getTargetName());
                statement.setString(4, relation.getRelationType().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<ClassInfo> loadClasses(Connection connection, long analysisId) throws SQLException {
        String classSql = """
                SELECT id, simple_name, package_name, kind
                FROM diagram_class
                WHERE analysis_id = ?
                ORDER BY id
                """;

        String memberSql = """
                SELECT member_type, signature
                FROM diagram_class_member
                WHERE class_id = ?
                ORDER BY sort_order, id
                """;

        List<ClassInfo> classes = new ArrayList<>();
        try (PreparedStatement classStatement = connection.prepareStatement(classSql)) {
            classStatement.setLong(1, analysisId);
            try (ResultSet classRows = classStatement.executeQuery()) {
                while (classRows.next()) {
                    long classId = classRows.getLong("id");
                    String simpleName = classRows.getString("simple_name");
                    String packageName = classRows.getString("package_name");
                    String kind = classRows.getString("kind");

                    List<String> fields = new ArrayList<>();
                    List<String> methods = new ArrayList<>();

                    try (PreparedStatement memberStatement = connection.prepareStatement(memberSql)) {
                        memberStatement.setLong(1, classId);
                        try (ResultSet memberRows = memberStatement.executeQuery()) {
                            while (memberRows.next()) {
                                String memberType = memberRows.getString("member_type");
                                String signature = memberRows.getString("signature");
                                if ("field".equals(memberType)) {
                                    fields.add(signature);
                                } else {
                                    methods.add(signature);
                                }
                            }
                        }
                    }

                    classes.add(new ClassInfo(packageName, simpleName, kind, fields, methods));
                }
            }
        }
        return classes;
    }

    private List<RelationInfo> loadRelations(Connection connection, long analysisId) throws SQLException {
        String sql = """
                SELECT source_name, target_name, relation_type
                FROM diagram_relation
                WHERE analysis_id = ?
                ORDER BY id
                """;

        List<RelationInfo> relations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, analysisId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    relations.add(new RelationInfo(
                            resultSet.getString("source_name"),
                            resultSet.getString("target_name"),
                            RelationType.valueOf(resultSet.getString("relation_type"))
                    ));
                }
            }
        }
        return relations;
    }

    private AnalysisSummary mapAnalysisSummary(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        LocalDateTime created = createdAt == null ? null : createdAt.toLocalDateTime();
        return new AnalysisSummary(
                resultSet.getLong("id"),
                resultSet.getString("project_path"),
                resultSet.getInt("class_count"),
                resultSet.getInt("relation_count"),
                created
        );
    }
}
