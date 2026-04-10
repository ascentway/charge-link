-- V2__Update_Nearby_Function.sql
-- We relax the is_verified = true constraint so that scraped data (default false)
-- is visible to users right away.

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
    -- Relaxing this filter:
    -- and s.is_verified = true
    and  c.is_active = true
    and  (connector_filter is null or c.connector_type = connector_filter)
  group  by s.id, s.name, s.address, s.city, s.geo_point, n.name
  order  by distance_km asc;
end;
$$ language plpgsql;
