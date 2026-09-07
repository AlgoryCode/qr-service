DO $$
DECLARE
    v_package_id    BIGINT;
    v_package_code  VARCHAR(64) := 'ULTIMATE_TRIAL_PACKAGE';
    v_duration_days INTEGER     := 15;
    v_starts_at     TIMESTAMP   := NOW();
    v_ends_at       TIMESTAMP   := NOW() + INTERVAL '15 days';
BEGIN
    SELECT id INTO v_package_id
    FROM tbl_plan_package
    WHERE code = v_package_code
      AND active = TRUE
    LIMIT 1;

    IF v_package_id IS NULL THEN
        RAISE NOTICE 'ULTIMATE_TRIAL_PACKAGE bulunamadi, migration atlaniyor.';
        RETURN;
    END IF;

    -- 1. Trial log: aktif ucretli paketi olmayan, henuz trial log'u bulunmayan kullanicilara ata
    INSERT INTO tbl_trial_log (
        user_id, package_id, package_code,
        started_at, ends_at, duration_days, status, created_at
    )
    SELECT
        u.id,
        v_package_id,
        v_package_code,
        v_starts_at,
        v_ends_at,
        v_duration_days,
        'ACTIVE',
        NOW()
    FROM tbl_user u
    WHERE NOT EXISTS (
        SELECT 1 FROM tbl_trial_log t WHERE t.user_id = u.id
    )
    AND NOT EXISTS (
        SELECT 1 FROM tbl_purchase p
        WHERE p.user_id      = u.id
          AND p.status       = 'ACTIVE'
          AND p.purchase_type = 'PAID'
    )
    ON CONFLICT ON CONSTRAINT uk_trial_log_user_id DO NOTHING;

    -- 2. Fulfillment: yeni olusturulan trial log'lar icin fulfillment kaydi ac
    INSERT INTO tbl_fulfillment (
        user_id, trial_log_id, package_id,
        status, starts_at, expires_at, created_at
    )
    SELECT
        tl.user_id,
        tl.id,
        v_package_id,
        'ACTIVE',
        tl.started_at,
        tl.ends_at,
        NOW()
    FROM tbl_trial_log tl
    WHERE tl.package_code = v_package_code
      AND NOT EXISTS (
          SELECT 1 FROM tbl_fulfillment f WHERE f.trial_log_id = tl.id
      );

    -- 3. Fulfillment detail: ULTIMATE_TRIAL_PACKAGE urunleri icin detay satirlari
    INSERT INTO tbl_fulfillment_detail (
        fulfillment_id, user_id, product_id,
        product_type_id, feature_code, scope_code,
        quantity, unlimited, used_quantity,
        source, starts_at, expires_at, version, created_at
    )
    SELECT
        f.id,
        f.user_id,
        pr.id,
        'PACKAGE_PRODUCT',
        COALESCE(NULLIF(TRIM(pr.feature_code), ''), pr.code),
        pr.scope_code,
        CASE WHEN ppi.unlimited THEN 0 ELSE ppi.quantity END,
        ppi.unlimited,
        0,
        'ONBOARDING_PACKAGE',
        f.starts_at,
        f.expires_at,
        0,
        NOW()
    FROM tbl_fulfillment f
    JOIN tbl_trial_log tl
        ON tl.id           = f.trial_log_id
       AND tl.package_code = v_package_code
    JOIN tbl_plan_package_item ppi
        ON ppi.package_id = v_package_id
    JOIN tbl_product pr
        ON pr.id = ppi.product_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM tbl_fulfillment_detail d
        WHERE d.fulfillment_id = f.id
          AND d.product_id     = pr.id
    );

    RAISE NOTICE 'V94 tamamlandi: ULTIMATE_TRIAL_PACKAGE atanan yeni trial log sayisi: %',
        (SELECT COUNT(*) FROM tbl_trial_log WHERE package_code = v_package_code);
END $$;
