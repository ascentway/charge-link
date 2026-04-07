-- Add email column to users table to store the user's email locally
-- The email comes from Supabase Auth and is synced during login/signup

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT UNIQUE;
