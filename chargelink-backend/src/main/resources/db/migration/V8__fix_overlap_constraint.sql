-- Fix the no_overlap constraint to also exclude 'expired' bookings from blocking the charger slot.
-- Drop the old constraint and recreate it with the updated exclusion list.

ALTER TABLE public.bookings DROP CONSTRAINT IF EXISTS no_overlap;

ALTER TABLE public.bookings
    ADD CONSTRAINT no_overlap EXCLUDE USING gist (
        charger_id WITH =,
        tstzrange(slot_start, slot_end) WITH &&
    ) WHERE (status NOT IN ('cancelled', 'no_show', 'expired'));
