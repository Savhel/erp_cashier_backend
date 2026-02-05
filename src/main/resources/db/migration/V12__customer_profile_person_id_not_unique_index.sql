ALTER TABLE customer_profile
    DROP CONSTRAINT IF EXISTS customer_profile_person_id_key;

DROP INDEX IF EXISTS customer_profile_person_id_key;
