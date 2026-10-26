-- The Care module (docs/ecosystem.md, section 6): routines, calm breaks, mood check-ins and reflections. Care lives on the
-- device by default; this schema holds its cloud copy only for players who turn sync on. It is the player's alone: no
-- friend, leaderboard or other module reads it, and Care reaches the shared game only through effort events with no
-- text, mood or health value (care-core, CareEvent).

create schema care;

-- One document per player, Care's own and apart from Plan's (plan.saves), with its own version.
create table care.saves (
  user_id    uuid primary key references platform.profiles (user_id) on delete cascade,
  version    int not null default 1 check (version > 0),
  data       jsonb not null check (jsonb_typeof(data) = 'object' and not data ? 'player'),
  updated_at timestamptz not null default now()
);

alter table care.saves enable row level security;
-- A player can read their copy and delete it (turning sync off), never write it directly.
create policy saves_own_read on care.saves for select to authenticated using (user_id = (select auth.uid()));
create policy saves_own_delete on care.saves for delete to authenticated using (user_id = (select auth.uid()));
grant usage on schema care to authenticated, service_role;
grant select, delete on care.saves to authenticated;
grant all on care.saves to service_role;

-- {"version": n, "data": {...}}, or version 0 and no data when nothing has been uploaded.
create function care.pull_save(p_user uuid) returns jsonb
  language sql stable security definer set search_path = '' as $$
  select coalesce(
    (select jsonb_build_object('version', version, 'data', data) from care.saves where user_id = p_user),
    jsonb_build_object('version', 0, 'data', null))
$$;

-- Saves Care's document. p_expected_version is the version the client last saw (0 for the first upload); a stale one
-- raises serialization_failure (40001). Nothing in it touches the game. Returns the new version.
create function care.push_save(p_user uuid, p_expected_version int, p_data jsonb) returns int
  language plpgsql security definer set search_path = '' as $$
declare
  current_version int;
begin
  select version into current_version from care.saves where user_id = p_user for update;
  if coalesce(current_version, 0) <> p_expected_version then
    raise exception 'care save has changed (expected version %, found %)', p_expected_version, coalesce(current_version, 0)
      using errcode = 'serialization_failure';
  end if;
  insert into care.saves (user_id, version, data) values (p_user, 1, p_data)
  on conflict (user_id) do update set version = care.saves.version + 1, data = excluded.data, updated_at = now();
  return p_expected_version + 1;
end $$;

revoke execute on function care.pull_save(uuid), care.push_save(uuid, int, jsonb) from public, anon, authenticated;
grant execute on function care.pull_save(uuid), care.push_save(uuid, int, jsonb) to service_role;
