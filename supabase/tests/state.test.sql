-- Rank thresholds, and the engine's golden vectors through load_state / apply_outcome.
select test.eq('thresholds match TrainerRank', platform.rank_thresholds(), :'thresholds'::int[]);
select test.eq('rank 0', platform.rank_for(0), 0);
select test.eq('rank 0 at 624', platform.rank_for(624), 0);
select test.eq('rank 1 at 625', platform.rank_for(625), 1);
select test.eq('rank 8 at 10624', platform.rank_for(10624), 8);
select test.eq('top rank', platform.rank_for(10625), 9);
select test.eq('beyond the top', platform.rank_for(10000000), 9);

\set golden `cat "$GOLDEN"`
create temp table golden as select :'golden'::jsonb as j;
grant select on golden to public;

insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000a1', 'state@example.com');
select test.eq('profile created with the account', (select count(*) from platform.profiles where user_id = '00000000-0000-0000-0000-0000000000a1'), 1::bigint);

-- Normal form for comparing states: the order of `caught` carries no meaning.
create function pg_temp.norm(s jsonb) returns jsonb language sql as $$
  select jsonb_set(s, '{caught}', (select coalesce(jsonb_agg(c order by c::int), '[]') from jsonb_array_elements_text(s->'caught') c))
$$;

set role service_role;

-- An empty player loads as the engine's empty state.
select test.eq('empty state', pg_temp.norm(platform.load_state('00000000-0000-0000-0000-0000000000a1')->'state'),
  '{"pokemon":[],"buddyId":null,"inventory":{"counts":{}},"coins":0,"eggMeter":0,"trainerPoints":0,"caught":[]}'::jsonb);

-- Save the golden starting state, then load it back unchanged.
select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 0, 'plan', 'test', 'seed-state', 0, '{}',
  jsonb_build_object('state', (select (j->>'state')::jsonb from golden), 'rewards', '[]'::jsonb));
select test.eq('round trip', pg_temp.norm(platform.load_state('00000000-0000-0000-0000-0000000000a1')->'state'),
  pg_temp.norm((select (j->>'state')::jsonb from golden)));
select test.eq('version after first save', (platform.load_state('00000000-0000-0000-0000-0000000000a1')->>'version')::int, 1);

-- Apply the engine's real outcome (a hatch, coins, an item, ...) and load it back.
select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 1, 'plan', 'focus', 'session-1',
  (select (j->>'effortSeed')::int from golden), (select (j->>'effortEvent')::jsonb from golden), (select (j->>'effortOutcome')::jsonb from golden));
select test.eq('engine outcome round trip', pg_temp.norm(platform.load_state('00000000-0000-0000-0000-0000000000a1')->'state'),
  pg_temp.norm((select ((j->>'effortOutcome')::jsonb)->'state' from golden)));
select test.eq('ledger row', (select count(*) from platform.reward_ledger where user_id = '00000000-0000-0000-0000-0000000000a1'), 2::bigint);
select test.eq('points delta recorded', (select points_delta from platform.reward_ledger where idempotency_key = 'session-1'), 6);

-- A retry with the same key changes nothing and returns the first result.
select test.eq('retry is not applied', (platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 1, 'plan', 'focus', 'session-1', 0, '{}',
  '{"state":{},"rewards":[]}'))->>'applied', 'false');
select test.eq('retry leaves the version', (platform.load_state('00000000-0000-0000-0000-0000000000a1')->>'version')::int, 2);

-- A stale version is refused.
select test.throws('stale version', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 1, 'plan', 'focus', 'session-2', 0, '{}',
  jsonb_build_object('state', (select (j->>'state')::jsonb from golden), 'rewards', '[]'::jsonb))$$, '40001');

-- The state cannot lose trainer points or caught species.
select test.throws('points cannot drop', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 2, 'plan', 'x', 'k1', 0, '{}',
  jsonb_build_object('state', (select (j->>'state')::jsonb from golden), 'rewards', '[]'::jsonb))$$, 'P0001');
select test.throws('caught cannot shrink', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 2, 'plan', 'x', 'k2', 0, '{}',
  jsonb_set(jsonb_build_object('state', (select ((j->>'effortOutcome')::jsonb)->'state' from golden), 'rewards', '[]'::jsonb), '{state,caught}', '[25]'))$$, 'P0001');

-- Seven Pokémon in the party is refused, and the whole save is rolled back.
select test.throws('party of seven', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 2, 'plan', 'x', 'k3', 0, '{}',
  jsonb_build_object('rewards', '[]'::jsonb, 'state', jsonb_set(jsonb_set(
    (select ((j->>'effortOutcome')::jsonb)->'state' from golden), '{buddyId}', '"m1"'), '{pokemon}',
    (select jsonb_agg(jsonb_set(jsonb_set(p, '{id}', to_jsonb('m' || n)), '{inParty}', 'true'))
       from generate_series(1, 7) n, (select ((j->>'effortOutcome')::jsonb)->'state'->'pokemon'->0 as p from golden) q))))$$, '23514');
select test.eq('nothing changed after the failed save', (platform.load_state('00000000-0000-0000-0000-0000000000a1')->>'version')::int, 2);

-- A buddy that is not in the collection is refused.
select test.throws('unknown buddy', $$select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 2, 'plan', 'x', 'k4', 0, '{}',
  jsonb_set(jsonb_build_object('state', (select ((j->>'effortOutcome')::jsonb)->'state' from golden), 'rewards', '[]'::jsonb), '{state,buddyId}', '"nobody"'))$$, '23503');

-- A transfer removes the Pokémon but keeps its species in `caught`.
select platform.apply_outcome('00000000-0000-0000-0000-0000000000a1', 2, 'plan', 'x', 'transfer-1', 0, '{}',
  jsonb_build_object('rewards', '[]'::jsonb, 'state', jsonb_set(
    (select ((j->>'effortOutcome')::jsonb)->'state' from golden), '{pokemon}',
    jsonb_build_array((select ((j->>'effortOutcome')::jsonb)->'state'->'pokemon'->0 from golden)))));
reset role;
select test.eq('one Pokémon left', (select count(*) from platform.pokemon where user_id = '00000000-0000-0000-0000-0000000000a1'), 1::bigint);
select test.eq('caught kept after transfer', (select cardinality(caught) from platform.profiles where user_id = '00000000-0000-0000-0000-0000000000a1'), 3);
