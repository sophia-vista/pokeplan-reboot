-- Cloud save: the whole app document of a player, so phone, desktop and web stay in step. The game state part of it (Pokémon,
-- bag, coins, rank) is saved through apply_outcome like everything else and is always read back from the shared tables, so
-- gifts and rewards from other apps are never lost; the rest (tasks, habits, goals, settings...) lives in `data`.

create table platform.cloud_saves (
  user_id    uuid primary key references platform.profiles (user_id) on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);

alter table platform.cloud_saves enable row level security;
create policy cloud_saves_own on platform.cloud_saves for select to authenticated using (user_id = (select auth.uid()));
grant select on platform.cloud_saves to authenticated;
grant all on platform.cloud_saves to service_role;

-- The player's save: {"version": n, "data": {...}} with `data.player` taken from the shared tables, or version only when
-- nothing has been uploaded yet.
create function platform.pull_save(p_user uuid) returns jsonb
  language plpgsql stable security definer set search_path = '' as $$
declare
  loaded jsonb := platform.load_state(p_user);
  doc jsonb;
begin
  select data into doc from platform.cloud_saves where user_id = p_user;
  if doc is null then
    return jsonb_build_object('version', (loaded->>'version')::int, 'data', null);
  end if;
  return jsonb_build_object('version', (loaded->>'version')::int, 'data', jsonb_set(doc, '{player}', loaded->'state'));
end $$;

-- Saves a document. p_expected_version must be the version the client last saw (stale: serialization_failure, 40001).
-- The player part goes through apply_outcome, so points cannot drop, caught species cannot vanish, the party and Guardian
-- slots are checked. Returns the new version.
create function platform.push_save(p_user uuid, p_expected_version int, p_data jsonb) returns int
  language plpgsql security definer set search_path = '' as $$
declare
  result jsonb;
begin
  result := platform.apply_outcome(
    p_user, p_expected_version, 'sync', 'snapshot', 'sync-' || gen_random_uuid()::text, 0, '{}',
    jsonb_build_object('state', p_data->'player', 'rewards', '[]'::jsonb));
  insert into platform.cloud_saves (user_id, data) values (p_user, p_data)
  on conflict (user_id) do update set data = excluded.data, updated_at = now();
  return (result->>'version')::int;
end $$;

revoke execute on function platform.pull_save(uuid), platform.push_save(uuid, int, jsonb) from public, anon, authenticated;
grant execute on function platform.pull_save(uuid), platform.push_save(uuid, int, jsonb) to service_role;
