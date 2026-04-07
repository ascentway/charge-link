-- Ensure phone is stored as BIGINT (10-digit Indian mobile numbers).
-- If the column is already BIGINT this is a no-op for the type change.
-- Also add a CHECK constraint to enforce exactly 10 digits.

ALTER TABLE users
    ALTER COLUMN phone TYPE BIGINT USING phone::BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'users_phone_10_digits'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT users_phone_10_digits
            CHECK (phone >= 1000000000 AND phone <= 9999999999);
    END IF;
END $$;
