CREATE TABLE tbl_stock_ingredient (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT         NOT NULL,
    branch_id     BIGINT         NOT NULL,
    name          VARCHAR(160)   NOT NULL,
    unit          VARCHAR(8)     NOT NULL,
    quantity      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    min_quantity  NUMERIC(14, 3) NOT NULL DEFAULT 0,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted    BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_stock_ingredient_branch
    ON tbl_stock_ingredient (branch_id, user_id)
    WHERE is_deleted = FALSE;

CREATE TABLE tbl_stock_recipe_line (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT         NOT NULL,
    ingredient_id      BIGINT         NOT NULL,
    quantity_per_sale  NUMERIC(14, 3) NOT NULL,
    CONSTRAINT uq_stock_recipe_product_ingredient UNIQUE (product_id, ingredient_id),
    CONSTRAINT chk_stock_recipe_qty CHECK (quantity_per_sale > 0),
    CONSTRAINT fk_stock_recipe_product
        FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_recipe_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES tbl_stock_ingredient (id) ON DELETE CASCADE
);

CREATE INDEX idx_stock_recipe_product ON tbl_stock_recipe_line (product_id);
CREATE INDEX idx_stock_recipe_ingredient ON tbl_stock_recipe_line (ingredient_id);

CREATE TABLE tbl_stock_movement (
    id              BIGSERIAL PRIMARY KEY,
    ingredient_id   BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    branch_id       BIGINT         NOT NULL,
    kind            VARCHAR(16)    NOT NULL,
    quantity_delta  NUMERIC(14, 3) NOT NULL,
    balance_after   NUMERIC(14, 3) NOT NULL,
    source_type     VARCHAR(16)    NOT NULL,
    source_id       BIGINT,
    note            VARCHAR(500),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_stock_movement_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES tbl_stock_ingredient (id)
);

CREATE INDEX idx_stock_movement_ingredient_created
    ON tbl_stock_movement (ingredient_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_stock_movement_source
    ON tbl_stock_movement (ingredient_id, kind, source_type, source_id)
    WHERE source_type <> 'MANUAL' AND source_id IS NOT NULL;
