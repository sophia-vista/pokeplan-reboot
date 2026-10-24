-- Egg tickets (offline hatching, see "Focus" in docs/design.md): random seeds the server hands to one device ahead of time.
-- The phone spends one when a focus session starts and rolls the egg from it, so an egg hatches without a connection and
-- the dice are fixed before the session. Each sync reports the spent tickets and tops the stock back up.

create table platform.egg_tickets (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references platform.profiles (user_id) on delete cascade,
  device     text not null check (length(device) between 1 and 200),
  seed       int not null,
  issued_at  timestamptz not null default now(),
  -- Set once, when the device reports the ticket spent.
  spent_at   timestamptz,
  started_at timestamptz,
  pokemon_id text
);

create index egg_tickets_unspent on platform.egg_tickets (user_id, device) where spent_at is null;

alter table platform.egg_tickets enable row level security;
create policy egg_tickets_own on platform.egg_tickets for select to authenticated using (user_id = (select auth.uid()));
grant select on platform.egg_tickets to authenticated;
grant all on platform.egg_tickets to service_role;

-- Records the spent tickets and returns the device's unspent stock, topped up to p_stock:
--   p_spent: [{"id": uuid, "startedAtMillis": n, "pokemonId": text or null}]  (null when the session failed)
--   returns {"tickets": [{"id", "seed", "issuedAtMillis"}], "refused": [ids]}
-- A ticket is accepted once, from the device it was issued to, for a session that started after it was issued (ten
-- minutes of clock difference allowed). Reporting the same spend again is accepted, so a retried sync is harmless.
create function platform.egg_tickets(p_user uuid, p_device text, p_spent jsonb, p_stock int) returns jsonb
  language plpgsql security definer set search_path = '' as $$
declare
  s jsonb;
  ticket_id uuid;
  started timestamptz;
  pokemon text;
  refused jsonb := '[]'::jsonb;
  unspent int;
begin
  for s in select * from jsonb_array_elements(coalesce(p_spent, '[]'::jsonb)) loop
    ticket_id := (s->>'id')::uuid;
    started := to_timestamp((s->>'startedAtMillis')::bigint / 1000.0);
    pokemon := s->>'pokemonId';
    update platform.egg_tickets set spent_at = now(), started_at = started, pokemon_id = pokemon
      where id = ticket_id and user_id = p_user and device = p_device and spent_at is null
        and issued_at <= started + interval '10 minutes' and started <= now() + interval '10 minutes';
    if not found and not exists (
      select 1 from platform.egg_tickets
      where id = ticket_id and user_id = p_user and device = p_device and spent_at is not null
        and started_at = started and pokemon_id is not distinct from pokemon
    ) then
      refused := refused || to_jsonb(ticket_id::text);
    end if;
  end loop;

  select count(*) into unspent from platform.egg_tickets where user_id = p_user and device = p_device and spent_at is null;
  -- Seeds come from gen_random_uuid, which is cryptographically random.
  insert into platform.egg_tickets (user_id, device, seed)
    select p_user, p_device, ('x' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8))::bit(32)::int
    from generate_series(1, greatest(p_stock - unspent, 0));

  return jsonb_build_object(
    'tickets', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', id::text, 'seed', seed, 'issuedAtMillis', (extract(epoch from issued_at) * 1000)::bigint
      ) order by issued_at, id)
      from platform.egg_tickets where user_id = p_user and device = p_device and spent_at is null
    ), '[]'::jsonb),
    'refused', refused);
end $$;

revoke execute on function platform.egg_tickets(uuid, text, jsonb, int) from public, anon, authenticated;
grant execute on function platform.egg_tickets(uuid, text, jsonb, int) to service_role;
