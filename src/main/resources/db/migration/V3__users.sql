-- Login accounts for the API. Kept separate from the ledger: accounts.owner_id is an opaque id
-- (in a larger system it would come from an identity service), so there is deliberately no FK.
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- DEMO USERS ONLY. The passwords (documented in the README) are public; never seed real users
-- like this. Hashes are BCrypt, cost 10.
--   rider@demo.local / rider-demo-123  (USER)
--   admin@demo.local / admin-demo-123  (ADMIN)
INSERT INTO users (id, email, password_hash, role) VALUES
    ('b7d4c1a0-5e3f-4c2a-9d1e-1a2b3c4d5e01', 'rider@demo.local',
     '$2b$10$tE82cyYEYGJGx1szr9rWguXyD5OvblyyrssjOHXlB1j8w1RE.x9qq', 'USER'),
    ('b7d4c1a0-5e3f-4c2a-9d1e-1a2b3c4d5e02', 'admin@demo.local',
     '$2b$10$ZxVxzZaXeolbx9Zx0BPn1uyVDSv.WC9yJLaHNwA.UOnChK51M.6rm', 'ADMIN');
