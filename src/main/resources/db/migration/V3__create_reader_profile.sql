CREATE TABLE reader_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reader_no VARCHAR(50) NOT NULL,
    contact VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reader_profile_user_id UNIQUE (user_id),
    CONSTRAINT uk_reader_profile_reader_no UNIQUE (reader_no),
    CONSTRAINT fk_reader_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_reader_profile_reader_no CHECK (CHAR_LENGTH(TRIM(reader_no)) BETWEEN 1 AND 50),
    CONSTRAINT ck_reader_profile_contact CHECK (contact IS NULL OR CHAR_LENGTH(contact) <= 100)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
