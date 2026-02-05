CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_register_event_idempotency
    ON cash_register_event (idempotency)
    WHERE idempotency IS NOT NULL;
