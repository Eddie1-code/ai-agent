CREATE TABLE user_account (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              username VARCHAR(64) UNIQUE NOT NULL,
                              password_hash VARCHAR(128) NOT NULL,
                              nickname VARCHAR(64),
                              avatar_url VARCHAR(255),
                              created_at DATETIME NOT NULL,
                              updated_at DATETIME NOT NULL
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_session (
                              id VARCHAR(64) PRIMARY KEY,
                              user_id BIGINT NOT NULL,
                              title VARCHAR(128) NOT NULL,
                              mode VARCHAR(16) NOT NULL,
                              archived TINYINT(1) NOT NULL DEFAULT 0,
                              created_at DATETIME NOT NULL,
                              updated_at DATETIME NOT NULL,
                              KEY idx_user_updated (user_id, updated_at DESC)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_message (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              session_id VARCHAR(64) NOT NULL,
                              role VARCHAR(16) NOT NULL,    -- user / assistant / tool / system
                              content MEDIUMTEXT NOT NULL,
                              meta JSON,                    -- 工具调用参数/结果/图片URL列表
                              created_at DATETIME NOT NULL,
                              KEY idx_session_created (session_id, created_at)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE export_record (
                               id BIGINT PRIMARY KEY AUTO_INCREMENT,
                               user_id BIGINT NOT NULL,
                               session_id VARCHAR(64) NOT NULL,
                               source_message_id BIGINT,
                               file_path VARCHAR(255) NOT NULL,
                               created_at DATETIME NOT NULL
) DEFAULT CHARSET=utf8mb4;