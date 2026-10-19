-- The cloud save: push, pull, stale versions, and that the player part always comes from the shared tables.
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000e1', 'save@example.com');
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000e2', 'save2@example.com');

\set golden `cat "$GOLDEN"`
create temp table golden as select :'golden'::jsonb as j;
grant select on golden to public;

set role service_role;
-- Nothing uploaded yet.
select test.eq('empty save', platform.pull_save('00000000-0000-0000-0000-0000000000e1')->'data', 'null'::jsonb);
select test.eq('empty save version', (platform.pull_save('00000000-0000-0000-0000-0000000000e1')->>'version')::int, 0);

-- Push a document: the golden state as the player, plus some planner data.
select test.eq('first push', platform.push_save('00000000-0000-0000-0000-0000000000e1', 0,
  jsonb_build_object('onboarded', true, 'tasks', '[{"id":"t1"}]'::jsonb, 'player', (select (j->>'state')::jsonb from golden))), 1);
select test.eq('pulled version', (platform.pull_save('00000000-0000-0000-0000-0000000000e1')->>'version')::int, 1);
select test.eq('planner data comes back', platform.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'tasks', '[{"id":"t1"}]'::jsonb);
select test.eq('player comes from the tables', platform.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'player'->>'trainerPoints', '700');
select test.eq('pokemon were saved to the tables', (select count(*) from platform.pokemon where user_id = '00000000-0000-0000-0000-0000000000e1'), 1::bigint);

-- A stale version is refused.
select test.throws('stale push', $$select platform.push_save('00000000-0000-0000-0000-0000000000e1', 0,
  jsonb_build_object('player', (select (j->>'state')::jsonb from golden)))$$, '40001');

-- A change made elsewhere (a gift opened) shows up in the next pull, even though the document did not change.
reset role;
update platform.inventory set count = count + 5 where user_id = '00000000-0000-0000-0000-0000000000e1' and item_id = 'rare-candy';
set role service_role;
select test.eq('table changes reach the pulled player', platform.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'player'->'inventory'->'counts'->>'rare-candy', '17');

-- The rules of apply_outcome still hold: points cannot go down.
select test.throws('points cannot drop through a push', $$select platform.push_save('00000000-0000-0000-0000-0000000000e1', 1,
  jsonb_build_object('player', jsonb_set((select (j->>'state')::jsonb from golden), '{trainerPoints}', '10')))$$, 'P0001');
reset role;

-- Players only see their own save, and clients cannot write one.
select test.as_user('00000000-0000-0000-0000-0000000000e2');
set role authenticated;
select test.eq('another player sees nothing', (select count(*) from platform.cloud_saves), 0::bigint);
select test.throws('clients cannot write saves', $$insert into platform.cloud_saves (user_id, data) values ('00000000-0000-0000-0000-0000000000e2', '{}')$$, '42501');
select test.throws('clients cannot push', $$select platform.push_save('00000000-0000-0000-0000-0000000000e2', 0, '{}')$$, '42501');
reset role;
