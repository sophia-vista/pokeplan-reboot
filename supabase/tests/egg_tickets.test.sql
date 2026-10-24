-- Egg tickets: the stock is topped up per device, a spent ticket is accepted once, and clients cannot touch them.
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000f1', 'tickets@example.com');
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000f2', 'tickets2@example.com');

set role service_role;
create temp table issued as
  select platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone', '[]', 3) as r;
select test.eq('stock is filled', jsonb_array_length((select r->'tickets' from issued)), 3);
select test.eq('nothing refused', (select r->'refused' from issued), '[]'::jsonb);
select test.eq('stock is per device', jsonb_array_length(
  platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'tablet', '[]', 2)->'tickets'), 2);
select test.eq('asking again issues nothing new', (select count(*) from platform.egg_tickets where device = 'phone'), 3::bigint);

-- Spend the first one: it is gone from the stock, and a new one replaces it.
create temp table spend as
  select jsonb_build_array(jsonb_build_object(
    'id', (select r->'tickets'->0->>'id' from issued),
    'startedAtMillis', (extract(epoch from now()) * 1000)::bigint,
    'pokemonId', 'p1')) as j;
create temp table after_spend as
  select platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone', (select j from spend), 3) as r;
select test.eq('spent ticket accepted', (select r->'refused' from after_spend), '[]'::jsonb);
select test.eq('stock topped up', jsonb_array_length((select r->'tickets' from after_spend)), 3);
select test.eq('spent ticket left the stock', (select count(*) from jsonb_array_elements((select r->'tickets' from after_spend)) t
  where t->>'id' = (select r->'tickets'->0->>'id' from issued)), 0::bigint);
select test.eq('spend recorded', (select pokemon_id from platform.egg_tickets where id = (select (r->'tickets'->0->>'id')::uuid from issued)), 'p1');

-- A retried report is harmless; spending it on another egg is refused.
select test.eq('same report again', platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone', (select j from spend), 3)->'refused', '[]'::jsonb);
select test.eq('spent twice', jsonb_array_length(platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone',
  jsonb_set((select j from spend), '{0,pokemonId}', '"p2"'), 3)->'refused'), 1);

-- Only the device it was issued to, and only for a session that started after it was issued.
select test.eq('another device', jsonb_array_length(platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'tablet',
  jsonb_build_array(jsonb_build_object('id', (select r->'tickets'->1->>'id' from issued), 'startedAtMillis', (extract(epoch from now()) * 1000)::bigint, 'pokemonId', 'p3')), 3)->'refused'), 1);
select test.eq('another player', jsonb_array_length(platform.egg_tickets('00000000-0000-0000-0000-0000000000f2', 'phone',
  jsonb_build_array(jsonb_build_object('id', (select r->'tickets'->1->>'id' from issued), 'startedAtMillis', (extract(epoch from now()) * 1000)::bigint, 'pokemonId', 'p3')), 3)->'refused'), 1);
select test.eq('session before the ticket', jsonb_array_length(platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone',
  jsonb_build_array(jsonb_build_object('id', (select r->'tickets'->1->>'id' from issued), 'startedAtMillis', 0, 'pokemonId', 'p3')), 3)->'refused'), 1);
select test.eq('a failed session spends a ticket too', platform.egg_tickets('00000000-0000-0000-0000-0000000000f1', 'phone',
  jsonb_build_array(jsonb_build_object('id', (select r->'tickets'->1->>'id' from issued), 'startedAtMillis', (extract(epoch from now()) * 1000)::bigint, 'pokemonId', null)), 3)->'refused', '[]'::jsonb);
reset role;

-- Players see only their own tickets and cannot write or issue any.
select test.as_user('00000000-0000-0000-0000-0000000000f2');
set role authenticated;
select test.eq('another player sees only their own', (select count(*) from platform.egg_tickets where user_id <> '00000000-0000-0000-0000-0000000000f2'), 0::bigint);
select test.throws('clients cannot mint tickets', $$insert into platform.egg_tickets (user_id, device, seed) values ('00000000-0000-0000-0000-0000000000f2', 'x', 1)$$, '42501');
select test.throws('clients cannot call the function', $$select platform.egg_tickets('00000000-0000-0000-0000-0000000000f2', 'x', '[]', 5)$$, '42501');
reset role;
