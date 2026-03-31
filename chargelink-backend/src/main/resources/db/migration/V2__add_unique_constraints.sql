-- Idempotent script for adding constraints
DO $$
BEGIN
  -- Ensure stations can be upserted by external_id
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stations_external_id_key') THEN
    ALTER TABLE stations ADD CONSTRAINT stations_external_id_key UNIQUE (external_id);
  END IF;

  -- Ensure chargers can be upserted by the combination of station and code
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chargers_station_id_charger_code_key') THEN
    ALTER TABLE chargers ADD CONSTRAINT chargers_station_id_charger_code_key UNIQUE (station_id, charger_code);
  END IF;
END $$;