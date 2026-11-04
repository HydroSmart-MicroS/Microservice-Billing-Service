ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_code_check;
ALTER TABLE plans ADD COLUMN device_limit INTEGER NOT NULL DEFAULT 1 CHECK(device_limit>0);
INSERT INTO plans(code,min_price_minor,max_price_minor,configured_price_minor,billing_interval,device_limit)
 VALUES('black',12000,12000,12000,'month',4) ON CONFLICT(code) DO UPDATE SET min_price_minor=12000,max_price_minor=12000,configured_price_minor=12000,billing_interval='month',device_limit=4;
UPDATE subscriptions SET plan_code='black' WHERE plan_code='landlord';
DELETE FROM plans WHERE code='landlord';
UPDATE plans SET min_price_minor=4000,max_price_minor=4000,configured_price_minor=4000,billing_interval='month',device_limit=1 WHERE code='premium';
ALTER TABLE plans ADD CONSTRAINT plans_code_check CHECK(code IN ('premium','black'));
CREATE TABLE payment_orders (
 id UUID PRIMARY KEY,user_id TEXT NOT NULL,kind TEXT NOT NULL CHECK(kind IN('plan','water')),
 plan_code TEXT REFERENCES plans(code),unit_id UUID,invoice_month DATE,
 amount_minor INTEGER NOT NULL CHECK(amount_minor>0),currency TEXT NOT NULL DEFAULT 'PEN' CHECK(currency='PEN'),
 status TEXT NOT NULL DEFAULT 'creating' CHECK(status IN('creating','pending','paid','failed')),
 provider_session_id TEXT UNIQUE,checkout_url TEXT,verification_token TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),paid_at TIMESTAMPTZ,next_check_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK((kind='plan' AND plan_code IS NOT NULL AND unit_id IS NULL) OR (kind='water' AND plan_code IS NULL AND unit_id IS NOT NULL AND invoice_month IS NOT NULL))
);
CREATE UNIQUE INDEX one_pending_plan ON payment_orders(user_id) WHERE kind='plan' AND status IN('creating','pending');
CREATE UNIQUE INDEX one_paid_or_pending_water_bill ON payment_orders(unit_id,invoice_month) WHERE kind='water' AND status IN('creating','pending','paid');
CREATE TABLE memberships (
 user_id TEXT PRIMARY KEY,plan_code TEXT NOT NULL REFERENCES plans(code),paid_through TIMESTAMPTZ NOT NULL,
 last_payment_id UUID NOT NULL REFERENCES payment_orders(id),updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payments_user_idx ON payment_orders(user_id,created_at DESC);

