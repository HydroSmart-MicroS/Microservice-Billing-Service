-- Existing pending water orders receive the same one-time notice as newly prepared orders.
WITH pending AS (
    SELECT *, gen_random_uuid() AS event_id
    FROM payment_orders WHERE kind='water' AND status='pending'
)
INSERT INTO outbox_events(event_id,event_type,aggregate_id,payload)
SELECT event_id,'billing.water.payment.pending.v1',user_id,
    jsonb_build_object(
        'event_id',event_id::text,'event_type','billing.water.payment.pending.v1',
        'schema_version',1,'producer','billing','occurred_at',now(),
        'data',jsonb_build_object('user_id',user_id,'order_id',id::text,
            'unit_id',unit_id::text,'invoice_month',to_char(invoice_month,'YYYY-MM'),
            'amount_minor',amount_minor,'currency',currency))
FROM pending;
