CREATE TABLE IF NOT EXISTS analysis_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_path VARCHAR(1024) NOT NULL,
    class_count INT NOT NULL,
    relation_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_analysis_project_path (project_path(255)),
    INDEX idx_analysis_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagram_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    simple_name VARCHAR(255) NOT NULL,
    package_name VARCHAR(512) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    CONSTRAINT fk_diagram_class_analysis
        FOREIGN KEY (analysis_id) REFERENCES analysis_run(id) ON DELETE CASCADE,
    INDEX idx_diagram_class_analysis (analysis_id),
    INDEX idx_diagram_class_name (simple_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagram_class_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    member_type VARCHAR(16) NOT NULL,
    signature TEXT NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_diagram_class_member_class
        FOREIGN KEY (class_id) REFERENCES diagram_class(id) ON DELETE CASCADE,
    INDEX idx_diagram_class_member_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagram_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    CONSTRAINT fk_diagram_relation_analysis
        FOREIGN KEY (analysis_id) REFERENCES analysis_run(id) ON DELETE CASCADE,
    INDEX idx_diagram_relation_analysis (analysis_id),
    INDEX idx_diagram_relation_source (source_name),
    INDEX idx_diagram_relation_target (target_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
