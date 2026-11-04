ALTER TABLE payment_orders DROP CONSTRAINT payment_orders_status_check;
ALTER TABLE payment_orders ADD CONSTRAINT payment_orders_status_check CHECK(status IN('creating','pending','paid','failed','cancelled'));
ALTER TABLE payment_orders ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE memberships ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE memberships ADD COLUMN cancellation_requested_at TIMESTAMPTZ;
