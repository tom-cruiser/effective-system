-- One set of platform-owned system accounts per supported currency.
--   TOPUP_CLEARING  - money arriving from mobile-money providers (debited on top-up)
--   PAYOUT_CLEARING - money owed out to drivers' mobile-money accounts (credited on payout)
--   PLATFORM_FEES   - the platform's commission on rides (credited on ride payment)
-- System accounts may go negative: TOPUP_CLEARING's negative balance is exactly the
-- amount of outside money that has entered the wallets.
INSERT INTO accounts (id, owner_id, name, type, currency, status, balance_minor)
SELECT gen_random_uuid(), NULL, n.name, 'SYSTEM', c.code, 'ACTIVE', 0
FROM (VALUES ('TOPUP_CLEARING'), ('PAYOUT_CLEARING'), ('PLATFORM_FEES')) AS n(name)
CROSS JOIN (VALUES ('BIF'), ('RWF'), ('KES'), ('USD')) AS c(code);
