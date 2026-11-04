CREATE TABLE plans (
    code TEXT PRIMARY KEY CHECK (code IN ('premium','landlord')),
    currency CHAR(3) NOT NULL DEFAULT 'PEN' CHECK (currency = 'PEN'),
    min_price_minor INTEGER NOT NULL CHECK (min_price_minor > 0),
    max_price_minor INTEGER NOT NULL CHECK (max_price_minor >= min_price_minor),
    configured_price_minor INTEGER,
    billing_interval TEXT CHECK (billing_interval IN ('month','year')),
    stripe_price_id TEXT UNIQUE,
    CHECK (configured_price_minor IS NULL OR configured_price_minor BETWEEN min_price_minor AND max_price_minor)
);
INSERT INTO plans (code,min_price_minor,max_price_minor) VALUES ('premium',1500,2500),('landlord',4000,6000);
-- Precio final y periodicidad sin definir: no crear precios de Stripe automáticamente.
CREATE TABLE billing_customers (
    user_id TEXT PRIMARY KEY,
    stripe_customer_id TEXT NOT NULL UNIQUE
);
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES billing_customers(user_id),
    plan_code TEXT NOT NULL REFERENCES plans(code),
    stripe_subscription_id TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    current_period_end TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX subscriptions_user_idx ON subscriptions (user_id);
CREATE TABLE stripe_webhook_events (
    stripe_event_id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);


CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);
CREATE INDEX outbox_pending_idx ON outbox_events (occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
