CREATE TABLE privacy_receipts(user_hash TEXT PRIMARY KEY,completed BOOLEAN NOT NULL DEFAULT false,expires_at TIMESTAMPTZ NOT NULL);
CREATE TABLE anonymous_payment_archive(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),kind TEXT NOT NULL,plan_code TEXT,
 invoice_month DATE,amount_minor INTEGER NOT NULL,currency TEXT NOT NULL,
 paid_date DATE,expires_at TIMESTAMPTZ NOT NULL);
CREATE INDEX anonymous_payments_expiry ON anonymous_payment_archive(expires_at);
