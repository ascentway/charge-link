ALTER TABLE public.bookings
ADD COLUMN IF NOT EXISTS buffer_expires_at timestamptz;

UPDATE public.bookings
SET buffer_expires_at = slot_start + interval '10 minutes'
WHERE buffer_expires_at IS NULL AND status = 'pending';
