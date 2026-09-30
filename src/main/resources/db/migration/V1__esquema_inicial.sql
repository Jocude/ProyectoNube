-- Esquema inicial: equivale al que generaba Hibernate con ddl-auto=update.
-- Se conservan los nombres de restricciones generados por Hibernate para que las
-- bases de datos creadas antes de Flyway (marcadas como baseline en la versión 1)
-- y las creadas desde cero sean idénticas.
-- SQL estándar: válido en PostgreSQL y en H2 (tests).

CREATE TABLE users (
    id                    UUID          NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL,
    email                 VARCHAR(255)  NOT NULL,
    enabled               BOOLEAN       NOT NULL,
    failed_login_attempts INTEGER       NOT NULL,
    locked_until          TIMESTAMP(6),
    name                  VARCHAR(255)  NOT NULL,
    password              VARCHAR(255)  NOT NULL,
    role                  VARCHAR(255)  NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

CREATE TABLE folders (
    id        UUID         NOT NULL,
    name      VARCHAR(255) NOT NULL,
    owner_id  UUID         NOT NULL,
    parent_id UUID,
    CONSTRAINT folders_pkey PRIMARY KEY (id),
    CONSTRAINT uk810hcg1nyhjyo0q3gj4mrcdvo UNIQUE (name, parent_id, owner_id),
    CONSTRAINT fk5b5p96ewk9msg1omqhl0vttop FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fkqcp836dgme9195j0wy9v3b6o3 FOREIGN KEY (parent_id) REFERENCES folders (id)
);

CREATE TABLE file_metadata (
    id            UUID         NOT NULL,
    checksum      VARCHAR(64),
    content_type  VARCHAR(255),
    deleted_at    TIMESTAMP(6),
    file_size     BIGINT       NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(255) NOT NULL,
    uploaded_at   TIMESTAMP(6) NOT NULL,
    folder_id     UUID,
    owner_id      UUID         NOT NULL,
    CONSTRAINT file_metadata_pkey PRIMARY KEY (id),
    CONSTRAINT fkathy9mlhv0dar61y3dqpkj781 FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fkcogpptr1qjw5rnu3wpvu0nf19 FOREIGN KEY (folder_id) REFERENCES folders (id)
);

CREATE INDEX idx_file_deleted_at ON file_metadata (deleted_at);
CREATE INDEX idx_file_folder_id ON file_metadata (folder_id);
CREATE INDEX idx_file_original_name ON file_metadata (original_name);
CREATE INDEX idx_file_owner_id ON file_metadata (owner_id);

CREATE TABLE share_tokens (
    id             UUID         NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    download_count INTEGER      NOT NULL,
    expires_at     TIMESTAMP(6) NOT NULL,
    max_downloads  INTEGER,
    token          VARCHAR(64)  NOT NULL,
    file_id        UUID         NOT NULL,
    owner_id       UUID         NOT NULL,
    CONSTRAINT share_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT idx_share_token_value UNIQUE (token),
    CONSTRAINT fks1pej0vcs1duen6dgc24cjs6i FOREIGN KEY (file_id) REFERENCES file_metadata (id),
    CONSTRAINT fkjm9g6dhv4rgrlu78cy59ne0vr FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_share_file_id ON share_tokens (file_id);
