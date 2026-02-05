ALTER TABLE account
    ADD COLUMN organization_id TEXT;

ALTER TABLE account
    ADD CONSTRAINT account_organization_id_fkey
        FOREIGN KEY (organization_id)
        REFERENCES organization(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;
