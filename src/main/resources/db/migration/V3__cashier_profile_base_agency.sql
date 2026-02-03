ALTER TABLE cashier_profile
    ADD COLUMN base_agency_id TEXT;

ALTER TABLE cashier_profile
    ADD CONSTRAINT cashier_profile_base_agency_id_fkey
    FOREIGN KEY (base_agency_id)
    REFERENCES agency(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;
