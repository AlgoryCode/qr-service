INSERT INTO tbl_roles (code, name, description) VALUES
    ('MERCHANT', 'Merchant', 'Business owner'),
    ('ADMIN', 'Admin', 'Platform admin'),
    ('CUSTOMER', 'Customer', 'Store customer'),
    ('WAITER', 'Waiter', 'Merchant staff waiter'),
    ('KITCHEN', 'Kitchen', 'Merchant staff kitchen'),
    ('COURIER', 'Courier', 'Merchant staff courier')
ON CONFLICT (code) DO NOTHING;

INSERT INTO tbl_scopes (code, name, description) VALUES
    ('PANEL_ACCESS', 'Panel access', 'Merchant panel'),
    ('STAFF_ORDERS', 'Staff orders', 'Staff order operations'),
    ('STAFF_KITCHEN', 'Kitchen board', 'Kitchen operations'),
    ('STAFF_COURIER', 'Courier tasks', 'Courier operations'),
    ('CUSTOMER_STORE', 'Customer store', 'Customer storefront'),
    ('ADMIN_PLATFORM', 'Admin platform', 'Admin console'),
    ('ADMIN_IMPERSONATE', 'Admin impersonate', 'Impersonate merchants')
ON CONFLICT (code) DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'MERCHANT' AND s.code IN ('PANEL_ACCESS')
ON CONFLICT DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'WAITER' AND s.code IN ('STAFF_ORDERS')
ON CONFLICT DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'KITCHEN' AND s.code IN ('STAFF_KITCHEN', 'STAFF_ORDERS')
ON CONFLICT DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'COURIER' AND s.code IN ('STAFF_COURIER')
ON CONFLICT DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'CUSTOMER' AND s.code IN ('CUSTOMER_STORE')
ON CONFLICT DO NOTHING;

INSERT INTO tbl_role_scopes (role_id, scope_id)
SELECT r.id, s.id FROM tbl_roles r CROSS JOIN tbl_scopes s
WHERE r.code = 'ADMIN' AND s.code IN ('ADMIN_PLATFORM', 'ADMIN_IMPERSONATE')
ON CONFLICT DO NOTHING;
