-- ============================================================
-- ChargeLinK — Supabase Schema
-- Zero-cost aggregator for EV charger real-time availability
-- Paste this entire file into Supabase SQL Editor and run.
-- ============================================================

-- Enable PostGIS for geospatial queries (finding nearby stations)
create extension if not exists postgis;
-- Enable btree_gist for UUID equality in exclude constraints
create extension if not exists btree_gist;

-- ============================================================
-- 1. USERS
-- Supabase Auth handles passwords. This table extends auth.users.
-- ============================================================
create table if not exists public.users (
  id          uuid primary key references auth.users(id) on delete cascade,
  full_name   text,
  phone       text unique,
  auth_provider text default 'email',        -- 'email' | 'google' | 'phone'
  created_at  timestamptz default now()
);

-- Row Level Security: users can only read/edit their own row
alter table public.users enable row level security;
do $$ begin
  create policy "users: own row only"
    on public.users for all
    using (auth.uid() = id);
exception when duplicate_object then null; end $$;

-- ============================================================
-- 2. VEHICLES
-- A user can register multiple EVs. connector_type must match
-- charger connector_type for compatibility filtering.
-- ============================================================
create table if not exists public.vehicles (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references public.users(id) on delete cascade,
  registration_no     text unique not null,
  brand               text not null,
  model               text not null,
  connector_type      text not null,          -- 'CCS2' | 'CHAdeMO' | 'Type2' | 'GB/T' | 'Bharat AC' | 'Bharat DC'
  battery_capacity_kwh int,
  range_km            int,
  created_at          timestamptz default now()
);

alter table public.vehicles enable row level security;
do $$ begin
  create policy "vehicles: owner only"
    on public.vehicles for all
    using (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

create index if not exists idx_vehicles_user on public.vehicles(user_id);
create index if not exists idx_vehicles_connector on public.vehicles(connector_type);

-- ============================================================
-- 3. NETWORKS (Charging Operators / Aggregated Networks)
-- This is what makes ChargeLinK an aggregator — every charger
-- belongs to a network (Tata Power EV, ChargeZone, BPCL, etc.)
-- ============================================================
create table if not exists public.networks (
  id              uuid primary key default gen_random_uuid(),
  name            text unique not null,       -- 'Tata Power EV', 'ChargeZone', etc.
  slug            text unique not null,       -- 'tata-power-ev', 'chargezone'
  logo_url        text,
  website_url     text,
  api_base_url    text,                       -- filled once you get API access
  has_live_api    boolean default false,      -- false = crowdsourced, true = live API
  is_active       boolean default true,
  created_at      timestamptz default now()
);

-- Networks is public read (no auth needed to see charger networks)
alter table public.networks enable row level security;
do $$ begin
  create policy "networks: public read"
    on public.networks for select
    using (true);
exception when duplicate_object then null; end $$;

-- ============================================================
-- 4. STATIONS
-- A physical location with one or more chargers.
-- geo_point enables PostGIS radius queries (find nearby stations).
-- external_id links back to source data (OpenChargeMap OCMID, etc.)
-- ============================================================
create table if not exists public.stations (
  id                uuid primary key default gen_random_uuid(),
  network_id        uuid references public.networks(id) on delete set null,
  external_id       text,                         -- e.g. OpenChargeMap ID, OCPI location_id
  name              text not null,
  address           text,
  city              text,
  state             text,
  pincode           text,
  lat               double precision not null,
  lng               double precision not null,
  geo_point         geography(point, 4326)
                      generated always as (
                        st_point(lng, lat)::geography
                      ) stored,
  amenities         text[],                       -- ['cafe', 'restroom', 'parking', 'mall']
  operating_hours   jsonb,                        -- {"mon_fri": "06:00-22:00", "sat_sun": "08:00-20:00"}
  data_source       text default 'scraped',       -- 'scraped' | 'crowdsourced' | 'api' | 'operator'
  is_verified       boolean default false,
  last_verified_at  timestamptz,
  created_at        timestamptz default now(),
  updated_at        timestamptz default now(),

  unique(network_id, external_id)
);

alter table public.stations enable row level security;
do $$ begin
  create policy "stations: public read"
    on public.stations for select using (true);
exception when duplicate_object then null; end $$;

-- Geospatial index — critical for "find stations within X km" queries
create index if not exists idx_stations_geo on public.stations using gist(geo_point);
create index if not exists idx_stations_city on public.stations(city);
create index if not exists idx_stations_network on public.stations(network_id);

-- ============================================================
-- 5. CHARGERS
-- Individual charge points at a station.
-- current_status is the hot field — updated frequently via
-- crowdsource reports or live API polling.
-- ============================================================
create table if not exists public.chargers (
  id                  uuid primary key default gen_random_uuid(),
  station_id          uuid not null references public.stations(id) on delete cascade,
  charger_code        text,                         -- physical label on the unit, e.g. "CP-01"
  connector_type      text not null,                -- 'CCS2' | 'CHAdeMO' | 'Type2' | 'Bharat DC' | 'Bharat AC'
  power_kw            numeric(5,1),                 -- e.g. 7.2, 22.0, 60.0, 150.0
  current_type        text default 'DC',            -- 'AC' | 'DC'
  current_status      text default 'unknown',       -- 'available' | 'occupied' | 'faulted' | 'offline' | 'unknown'
  status_updated_at   timestamptz default now(),
  status_source       text default 'unknown',       -- 'crowdsourced' | 'api' | 'ocpp'
  price_per_kwh       numeric(6,2),
  price_per_min       numeric(6,2),
  is_active           boolean default true,
  created_at          timestamptz default now()
);

alter table public.chargers enable row level security;
do $$ begin
  create policy "chargers: public read"
    on public.chargers for select using (true);
exception when duplicate_object then null; end $$;

create index if not exists idx_chargers_station on public.chargers(station_id);
create index if not exists idx_chargers_status on public.chargers(current_status);
create index if not exists idx_chargers_connector on public.chargers(connector_type);

-- ============================================================
-- 6. STATUS REPORTS (Crowdsource Engine)
-- This is your moat before you have live APIs.
-- Users report what they actually see at the charger.
-- Trusted users get higher weight — build reputation over time.
-- ============================================================
create table if not exists public.status_reports (
  id                uuid primary key default gen_random_uuid(),
  charger_id        uuid not null references public.chargers(id) on delete cascade,
  reported_by       uuid references public.users(id) on delete set null,
  reported_status   text not null,               -- 'available' | 'occupied' | 'faulted' | 'offline'
  note              text,                         -- optional: "Screen broken but charging works"
  photo_url         text,                         -- optional proof photo (Supabase Storage)
  confidence        int default 5,                -- 1–10, auto-calculated from reporter history
  is_applied        boolean default false,         -- did this report update charger status?
  reported_at       timestamptz default now()
);

alter table public.status_reports enable row level security;
do $$ begin
  create policy "status_reports: auth users insert"
    on public.status_reports for insert
    with check (auth.uid() = reported_by);
exception when duplicate_object then null; end $$;

do $$ begin
  create policy "status_reports: public read"
    on public.status_reports for select using (true);
exception when duplicate_object then null; end $$;

create index if not exists idx_reports_charger on public.status_reports(charger_id);
create index if not exists idx_reports_reporter on public.status_reports(reported_by);
create index if not exists idx_reports_time on public.status_reports(reported_at desc);

-- ============================================================
-- 7. BOOKINGS
-- Slot reservations. A booking locks a charger for a time window.
-- Status flow: pending → confirmed → active → completed | cancelled
-- ============================================================
create table if not exists public.bookings (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.users(id) on delete cascade,
  charger_id    uuid not null references public.chargers(id) on delete cascade,
  vehicle_id    uuid references public.vehicles(id) on delete set null,
  slot_start    timestamptz not null,
  slot_end      timestamptz not null,
  status        text default 'pending',            -- 'pending' | 'confirmed' | 'active' | 'completed' | 'cancelled' | 'no_show'
  estimated_kwh numeric(5,1),
  notes         text,
  cancelled_at  timestamptz,
  cancel_reason text,
  created_at    timestamptz default now(),

  -- Prevent double-booking the same charger in the same slot
  constraint no_overlap exclude using gist (
    charger_id with =,
    tstzrange(slot_start, slot_end) with &&
  ) where (status not in ('cancelled', 'no_show'))
);

alter table public.bookings enable row level security;
do $$ begin
  create policy "bookings: owner only"
    on public.bookings for all
    using (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

create index if not exists idx_bookings_user on public.bookings(user_id);
create index if not exists idx_bookings_charger on public.bookings(charger_id);
create index if not exists idx_bookings_slot on public.bookings(slot_start, slot_end);
create index if not exists idx_bookings_status on public.bookings(status);

-- ============================================================
-- 8. SESSIONS
-- Actual charging sessions (created when booking goes active,
-- or walk-in sessions with no booking).
-- ============================================================
create table if not exists public.sessions (
  id                    uuid primary key default gen_random_uuid(),
  booking_id            uuid unique references public.bookings(id) on delete set null,
  user_id               uuid references public.users(id) on delete set null,
  charger_id            uuid not null references public.chargers(id) on delete cascade,
  energy_delivered_kwh  numeric(7,3),
  duration_minutes      int,
  amount_charged        numeric(8,2),
  currency              text default 'INR',
  payment_status        text default 'pending',   -- 'pending' | 'paid' | 'failed' | 'refunded'
  started_at            timestamptz not null,
  ended_at              timestamptz,
  created_at            timestamptz default now()
);

alter table public.sessions enable row level security;
do $$ begin
  create policy "sessions: owner only"
    on public.sessions for select
    using (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

create index if not exists idx_sessions_charger on public.sessions(charger_id);
create index if not exists idx_sessions_user on public.sessions(user_id);

-- ============================================================
-- 9. WAITLIST
-- When a charger slot is full, users join a waitlist.
-- Push notification sent when a slot opens up.
-- ============================================================
create table if not exists public.waitlist (
  id            uuid primary key default gen_random_uuid(),
  charger_id    uuid not null references public.chargers(id) on delete cascade,
  user_id       uuid not null references public.users(id) on delete cascade,
  vehicle_id    uuid references public.vehicles(id) on delete set null,
  wanted_from   timestamptz not null,
  wanted_to     timestamptz not null,
  status        text default 'waiting',           -- 'waiting' | 'notified' | 'booked' | 'expired'
  notified_at   timestamptz,
  joined_at     timestamptz default now(),

  unique(charger_id, user_id, wanted_from)
);

alter table public.waitlist enable row level security;
do $$ begin
  create policy "waitlist: owner only"
    on public.waitlist for all
    using (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

create index if not exists idx_waitlist_charger on public.waitlist(charger_id, wanted_from);

-- ============================================================
-- 11. TRIGGER: auto-update charger status on new crowd report
-- When 2+ reports in last 30 mins agree on a status → apply it
-- ============================================================
create or replace function update_charger_status_from_reports()
returns trigger as $$
declare
  consensus_status text;
  report_count     int;
begin
  select reported_status, count(*)
  into   consensus_status, report_count
  from   public.status_reports
  where  charger_id = new.charger_id
    and  reported_at > now() - interval '30 minutes'
  group  by reported_status
  order  by count(*) desc
  limit  1;

  if report_count >= 2 then
    update public.chargers
    set    current_status    = consensus_status,
           status_updated_at = now(),
           status_source     = 'crowdsourced'
    where  id = new.charger_id;

    update public.status_reports
    set    is_applied = true
    where  charger_id = new.charger_id
      and  reported_status = consensus_status
      and  reported_at > now() - interval '30 minutes';
  end if;

  return new;
end;
$$ language plpgsql security definer;

-- Trigger creation (no OR REPLACE for triggers, need to drop if exists or use a block)
do $$ begin
  if not exists (select 1 from pg_trigger where tgname = 'trg_update_charger_status') then
    create trigger trg_update_charger_status
    after insert on public.status_reports
    for each row execute function update_charger_status_from_reports();
  end if;
end $$;

-- ============================================================
-- 12. FUNCTION: find_nearby_stations
-- Core query your app will call constantly.
-- Returns stations within radius_km, filtered by connector_type.
-- ============================================================
create or replace function find_nearby_stations(
  user_lat        double precision,
  user_lng        double precision,
  radius_km       double precision default 5,
  connector_filter text default null
)
returns table (
  station_id   uuid,
  station_name text,
  address      text,
  city         text,
  distance_km  double precision,
  network_name text,
  available    bigint,
  total        bigint
) as $$
begin
  return query
  select
    s.id,
    s.name,
    s.address,
    s.city,
    round((st_distance(
      s.geo_point,
      st_point(user_lng, user_lat)::geography
    ) / 1000)::numeric, 2)::double precision  as distance_km,
    n.name                                     as network_name,
    count(c.id) filter (where c.current_status = 'available') as available,
    count(c.id)                                as total
  from   public.stations s
  join   public.chargers c on c.station_id = s.id
  left   join public.networks n on n.id = s.network_id
  where  st_dwithin(
           s.geo_point,
           st_point(user_lng, user_lat)::geography,
           radius_km * 1000
         )
    and  s.is_verified = true
    and  c.is_active = true
    and  (connector_filter is null or c.connector_type = connector_filter)
  group  by s.id, s.name, s.address, s.city, s.geo_point, n.name
  order  by distance_km asc;
end;
$$ language plpgsql;

-- ============================================================
-- 13. SEED: Insert sample networks
-- Add all major Indian EV charging operators as starting points
-- ============================================================
insert into public.networks (name, slug, has_live_api) values
  ('Tata Power EV',   'tata-power-ev',   false),
  ('ChargeZone',      'chargezone',       false),
  ('BPCL EV',        'bpcl-ev',          false),
  ('Ather Grid',     'ather-grid',        false),
  ('Statiq',         'statiq',            false),
  ('Volttic',        'volttic',           false),
  ('OpenChargeMap',  'opencharge-map',    true),
  ('EVSE India',     'evse-india',        false)
on conflict (slug) do nothing;
