-- Wallet ledger schema.
-- The database enforces what it can on its own (types, ranges, uniqueness, immutability)
-- so that a bug in the application cannot silently corrupt the ledger.

-- ---------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id            UUID         PRIMARY KEY,
    owner_id      UUID,
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    -- Cached balance in minor units; always equal to the sum of the account's postings.
    balance_minor BIGINT       NOT NULL DEFAULT 0,
    -- JPA optimistic-locking version; a safety net for non-ledger updates such as freezing.
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_accounts_type     CHECK (type IN ('USER_WALLET', 'DRIVER_WALLET', 'SYSTEM')),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('BIF', 'RWF', 'KES', 'USD')),
    CONSTRAINT chk_accounts_status   CHECK (status IN ('ACTIVE', 'FROZEN')),
    -- Wallets belong to someone; system accounts belong to the platform.
    CONSTRAINT chk_accounts_owner    CHECK ((type = 'SYSTEM') = (owner_id IS NULL)),
    -- Last line of defence for invariant 4: wallets never go negative.
    CONSTRAINT chk_accounts_wallet_non_negative CHECK (type = 'SYSTEM' OR balance_minor >= 0)
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
-- There is exactly one TOPUP_CLEARING / PAYOUT_CLEARING / PLATFORM_FEES account per currency.
CREATE UNIQUE INDEX uq_accounts_system_name_currency ON accounts (name, currency) WHERE type = 'SYSTEM';

-- ---------------------------------------------------------------------------
-- Ledger transactions (journal entries)
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_transactions (
    id                      UUID         PRIMARY KEY,
    type                    VARCHAR(20)  NOT NULL,
    idempotency_key         VARCHAR(100) NOT NULL,
    -- SHA-256 (hex) of the request body, to detect a reused key with a different payload.
    request_hash            VARCHAR(64)  NOT NULL,
    description             VARCHAR(255),
    -- e.g. the mobile-money provider's transaction id for a top-up.
    external_reference      VARCHAR(100),
    reverses_transaction_id UUID         REFERENCES ledger_transactions (id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_ledger_transactions_type
        CHECK (type IN ('TOP_UP', 'TRANSFER', 'RIDE_PAYMENT', 'PAYOUT', 'REVERSAL')),
    CONSTRAINT uq_ledger_transactions_idempotency_key UNIQUE (idempotency_key),
    -- A transaction can be reversed at most once.
    CONSTRAINT uq_ledger_transactions_reverses UNIQUE (reverses_transaction_id),
    -- Only reversals point at another transaction, and every reversal does.
    CONSTRAINT chk_ledger_transactions_reversal_link
        CHECK ((type = 'REVERSAL') = (reverses_transaction_id IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- Postings (journal lines)
-- ---------------------------------------------------------------------------
CREATE TABLE postings (
    id                  UUID        PRIMARY KEY,
    -- Strictly increasing insertion order. Postings to one account are written while that
    -- account is locked, so for any single account this matches the order they were applied
    -- (created_at cannot guarantee that: now() is the DB transaction's *start* time).
    entry_seq           BIGINT      GENERATED ALWAYS AS IDENTITY UNIQUE,
    transaction_id      UUID        NOT NULL REFERENCES ledger_transactions (id),
    account_id          UUID        NOT NULL REFERENCES accounts (id),
    direction           VARCHAR(6)  NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    -- The account's balance right after this posting was applied (for statements and audits).
    balance_after_minor BIGINT      NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_postings_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_postings_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_postings_currency CHECK (currency IN ('BIF', 'RWF', 'KES', 'USD'))
);

CREATE INDEX idx_postings_transaction_id ON postings (transaction_id);
-- Statements: one account's postings, newest first, optionally within a date range.
CREATE INDEX idx_postings_account_seq ON postings (account_id, entry_seq);
CREATE INDEX idx_postings_account_created_at ON postings (account_id, created_at);

-- ---------------------------------------------------------------------------
-- Immutability (invariant 5), enforced by the database itself.
-- Corrections are made with REVERSAL transactions, never by editing history.
-- ---------------------------------------------------------------------------
CREATE FUNCTION forbid_postings_mutation() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'postings are immutable: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_postings_no_update_delete
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION forbid_postings_mutation();

CREATE TRIGGER trg_postings_no_truncate
    BEFORE TRUNCATE ON postings
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_postings_mutation();
