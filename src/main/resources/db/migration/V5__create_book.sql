CREATE TABLE book (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(200) NOT NULL,
    publisher VARCHAR(200) NULL,
    published_date DATE NULL,
    isbn VARCHAR(13) NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_book_isbn UNIQUE (isbn),
    CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_book_title CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_book_author CHECK (CHAR_LENGTH(TRIM(author)) BETWEEN 1 AND 200),
    CONSTRAINT ck_book_publisher CHECK (publisher IS NULL OR CHAR_LENGTH(publisher) <= 200),
    CONSTRAINT ck_book_isbn_format CHECK (isbn IS NULL OR isbn REGEXP '^([0-9]{9}[0-9X]|[0-9]{13})$'),
    CONSTRAINT ck_book_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_book_version CHECK (version >= 0),
    CONSTRAINT ck_book_description CHECK (description IS NULL OR CHAR_LENGTH(description) <= 2000),
    INDEX ix_book_category_status (category_id, status)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
