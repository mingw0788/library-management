CREATE TABLE system_guard (
    guard_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (guard_key)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO system_guard (guard_key)
VALUES ('ACTIVE_ADMIN_SET');
