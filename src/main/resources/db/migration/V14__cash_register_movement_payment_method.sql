ALTER TABLE cash_register_movement
    ADD COLUMN reason_detail TEXT;

ALTER TABLE cash_register_movement
    ADD COLUMN payment_method TEXT;

ALTER TABLE cash_register_movement
    ADD COLUMN payment_method_other TEXT;
