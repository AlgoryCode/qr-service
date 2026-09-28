CREATE TABLE IF NOT EXISTS demo_onboarding_assignment (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    branch_id  BIGINT    NOT NULL,
    menu_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_demo_onboarding_assignment_user UNIQUE (user_id)
);
