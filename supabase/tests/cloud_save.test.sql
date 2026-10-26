-- The cloud save (plan.saves): push, pull, stale versions, that the player part always comes from the shared tables and
-- is never stored in the Plan document, and that the rules version reaches the ledger.
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000e1', 'save@example.com');
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000e2', 'save2@example.com');

\set golden `cat "$GOLDEN"`
create temp table golden as select :'golden'::jsonb as j;
grant select on golden to public;

set role service_role;
-- Nothing uploaded yet.
select test.eq('empty save', plan.pull_save('00000000-0000-0000-0000-0000000000e1')->'data', 'null'::jsonb);
select test.eq('empty save version', (plan.pull_save('00000000-0000-0000-0000-0000000000e1')->>'version')::int, 0);

-- Push a document: the golden state as the player, plus some planner data.
select test.eq('first push', plan.push_save('00000000-0000-0000-0000-0000000000e1', 0,
  jsonb_build_object('onboarded', true, 'tasks', '[{"id":"t1"}]'::jsonb, 'player', (select (j->>'state')::jsonb from golden))), 1);
select test.eq('pulled version', (plan.pull_save('00000000-0000-0000-0000-0000000000e1')->>'version')::int, 1);
select test.eq('planner data comes back', plan.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'tasks', '[{"id":"t1"}]'::jsonb);
select test.eq('player comes from the tables', plan.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'player'->>'trainerPoints', '700');
select test.eq('pokemon were saved to the tables', (select count(*) from platform.pokemon where user_id = '00000000-0000-0000-0000-0000000000e1'), 1::bigint);
select test.eq('the plan save holds no game state', (select data ? 'player' from plan.saves where user_id = '00000000-0000-0000-0000-0000000000e1'), false);
select test.eq('a push without a rules version records 0', (select rules_version from platform.reward_ledger
  where user_id = '00000000-0000-0000-0000-0000000000e1' and source = 'snapshot' order by id desc limit 1), 0);

-- A stale version is refused.
select test.throws('stale push', $$select plan.push_save('00000000-0000-0000-0000-0000000000e1', 0,
  jsonb_build_object('player', (select (j->>'state')::jsonb from golden)))$$, '40001');

-- A change made elsewhere (a gift opened) shows up in the next pull, even though the document did not change.
reset role;
update platform.inventory set count = count + 5 where user_id = '00000000-0000-0000-0000-0000000000e1' and item_id = 'rare-candy';
set role service_role;
select test.eq('table changes reach the pulled player', plan.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'player'->'inventory'->'counts'->>'rare-candy', '17');

-- The client's rules version is recorded with its snapshot.
select test.eq('second push', plan.push_save('00000000-0000-0000-0000-0000000000e1', 1,
  jsonb_build_object('onboarded', true, 'tasks', '[{"id":"t2"}]'::jsonb, 'player', (select (j->>'state')::jsonb from golden)), 7), 2);
select test.eq('the rules version reaches the ledger', (select rules_version from platform.reward_ledger
  where user_id = '00000000-0000-0000-0000-0000000000e1' and source = 'snapshot' order by id desc limit 1), 7);

-- The old names keep working until the reward function is redeployed.
select test.eq('old pull still works', platform.pull_save('00000000-0000-0000-0000-0000000000e1')->'data'->'tasks', '[{"id":"t2"}]'::jsonb);
select test.eq('old push still works', platform.push_save('00000000-0000-0000-0000-0000000000e1', 2,
  jsonb_build_object('tasks', '[{"id":"t3"}]'::jsonb, 'player', (select (j->>'state')::jsonb from golden))), 3);

-- The rules of apply_outcome still hold: points cannot go down.
select test.throws('points cannot drop through a push', $$select plan.push_save('00000000-0000-0000-0000-0000000000e1', 3,
  jsonb_build_object('player', jsonb_set((select (j->>'state')::jsonb from golden), '{trainerPoints}', '10')))$$, 'P0001');
reset role;

-- Players only see their own save, and clients cannot write one.
select test.as_user('00000000-0000-0000-0000-0000000000e2');
set role authenticated;
select test.eq('another player sees nothing', (select count(*) from plan.saves), 0::bigint);
select test.throws('clients cannot write saves', $$insert into plan.saves (user_id, data) values ('00000000-0000-0000-0000-0000000000e2', '{}')$$, '42501');
select test.throws('clients cannot push', $$select plan.push_save('00000000-0000-0000-0000-0000000000e2', 0, '{}')$$, '42501');
reset role;
