-- Row level security, column grants and the constraints, as a signed-in player would meet them.
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000b1', 'alice@example.com'),
  ('00000000-0000-0000-0000-0000000000b2', 'bob@example.com');

insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, caught_at) values
  ('00000000-0000-0000-0000-0000000000b1', 'a1', 25, 'pikachu', 'Pika', 'HARDY', 'Static', '{1,2,3,4,5,6}', 0),
  ('00000000-0000-0000-0000-0000000000b2', 'b1', 4, 'charmander', 'Char', 'HARDY', 'Blaze', '{1,2,3,4,5,6}', 0);

-- Table constraints.
select test.throws('IV above 31', $$insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, caught_at)
  values ('00000000-0000-0000-0000-0000000000b1', 'x', 1, 'bulbasaur', 'x', 'HARDY', 'Overgrow', '{32,0,0,0,0,0}', 0)$$, '23514');
select test.throws('EVs above 510', $$insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, evs, caught_at)
  values ('00000000-0000-0000-0000-0000000000b1', 'x', 1, 'bulbasaur', 'x', 'HARDY', 'Overgrow', '{0,0,0,0,0,0}', '{252,252,252,0,0,0}', 0)$$, '23514');
select test.throws('three training stats', $$update platform.pokemon set training_plan = '{A,B,C}' where id = 'a1'$$, '23514');

-- A player sees only their own rows.
select test.as_user('00000000-0000-0000-0000-0000000000b1');
set role authenticated;
select test.eq('own Pokémon only', (select count(*) from platform.pokemon), 1::bigint);
select test.eq('own profile only', (select count(*) from platform.profiles), 1::bigint);
select test.eq('cannot touch another player', test.rows($$update platform.profiles set trainer_name = 'Hacked' where user_id = '00000000-0000-0000-0000-0000000000b2'$$), 0::bigint);
select test.eq('cannot touch their Pokémon', test.rows($$update platform.pokemon set nickname = 'Hacked' where id = 'b1'$$), 0::bigint);

-- Allowed edits bump the version, so a stale outcome cannot overwrite them.
select test.eq('rename', test.rows($$update platform.pokemon set nickname = 'Sparky' where id = 'a1'$$), 1::bigint);
select test.eq('rename bumped the version', (select version from platform.profiles), 1);
select test.eq('set buddy', test.rows($$update platform.profiles set buddy_id = 'a1'$$), 1::bigint);
select test.eq('buddy bumped the version', (select version from platform.profiles), 2);

-- Game state is read-only.
select test.throws('no free coins', $$update platform.profiles set coins = 99999$$, '42501');
select test.throws('no free points', $$update platform.profiles set trainer_points = 99999$$, '42501');
select test.throws('no free shiny', $$update platform.pokemon set is_shiny = true$$, '42501');
select test.throws('no free level', $$update platform.pokemon set level = 100$$, '42501');
select test.throws('no inserting Pokémon', $$insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, caught_at)
  values ('00000000-0000-0000-0000-0000000000b1', 'x', 150, 'mewtwo', 'x', 'HARDY', 'Pressure', '{31,31,31,31,31,31}', 0)$$, '42501');
select test.throws('no inserting items', $$insert into platform.inventory (user_id, item_id, count) values ('00000000-0000-0000-0000-0000000000b1', 'rare-candy', 999)$$, '42501');
select test.throws('no calling apply_outcome', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000b1', 2, 'x', 'x', 'x', 0, '{}', '{}')$$, '42501');
select test.throws('no calling load_state', $$select platform.load_state('00000000-0000-0000-0000-0000000000b2')$$, '42501');
-- The buddy has to be the player's own Pokémon.
select test.throws('buddy must be theirs', $$set constraints all immediate; update platform.profiles set buddy_id = 'b1'$$, '23503');
reset role;

-- At most 6 in the party, even through the allowed column.
insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, caught_at)
  select '00000000-0000-0000-0000-0000000000b1', 'p' || n, 10, 'caterpie', 'c', 'HARDY', 'Shield Dust', '{0,0,0,0,0,0}', 0 from generate_series(1, 6) n;
select test.as_user('00000000-0000-0000-0000-0000000000b1');
set role authenticated;
select test.throws('party of seven', $$set constraints all immediate; update platform.pokemon set in_party = true$$, '23514');
select test.eq('party of six is fine', test.rows($$update platform.pokemon set in_party = true where id in ('a1', 'p1', 'p2', 'p3', 'p4', 'p5')$$), 6::bigint);
reset role;

-- Anonymous visitors see nothing.
set role anon;
select test.throws('anon cannot read', $$select * from platform.pokemon$$, '42501');
reset role;
