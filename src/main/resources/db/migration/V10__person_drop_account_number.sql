DROP INDEX IF EXISTS person_account_number_key;

ALTER TABLE person
    DROP COLUMN IF EXISTS account_number;
