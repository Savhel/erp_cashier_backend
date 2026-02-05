CREATE TABLE bill (
    id TEXT NOT NULL,
    organization_id TEXT NOT NULL,
    invoice_code TEXT NOT NULL,
    amount DECIMAL(65,30) NOT NULL,
    customer_name TEXT,
    due_date TIMESTAMP(3),
    payment_mode TEXT NOT NULL,
    account_id TEXT,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT bill_pkey PRIMARY KEY (id)
);

CREATE TABLE bill_item (
    id TEXT NOT NULL,
    bill_id TEXT NOT NULL,
    description TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    amount DECIMAL(65,30) NOT NULL,

    CONSTRAINT bill_item_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_bill_org
    ON bill (organization_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bill_org_invoice_code
    ON bill (organization_id, invoice_code);

CREATE INDEX IF NOT EXISTS idx_bill_item_bill_id
    ON bill_item (bill_id);
