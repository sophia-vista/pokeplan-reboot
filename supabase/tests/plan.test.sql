-- The planner's tables: what clients may write, the server-clock rules, and the limits.
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000c1', 'plan-alice@example.com'),
  ('00000000-0000-0000-0000-0000000000c2', 'plan-bob@example.com');

select test.as_user('00000000-0000-0000-0000-0000000000c1');
set role authenticated;

insert into plan.lists (id, name) values ('00000000-0000-0000-0000-00000000c0a1', 'Home');
insert into plan.series (id, title, recurrence) values ('00000000-0000-0000-0000-00000000c0b1', 'Water plants', '{"repeat":{"kind":"daily"}}');
insert into plan.tasks (id, title, rank, list_id) values ('00000000-0000-0000-0000-00000000c001', 'Write report', 'C', '00000000-0000-0000-0000-00000000c0a1');
insert into plan.tasks (id, title, rank, series_id) values ('00000000-0000-0000-0000-00000000c002', 'Water plants', 'D', '00000000-0000-0000-0000-00000000c0b1');
insert into plan.tasks (id, title, rank) values ('00000000-0000-0000-0000-00000000c003', 'Reply to mail', 'E');
select test.eq('own tasks', (select count(*) from plan.tasks), 3::bigint);

-- The server decides these.
select test.throws('cannot back-date a task', $$insert into plan.tasks (title, created_at) values ('x', now() - interval '1 day')$$, '42501');
select test.throws('cannot finish a task directly', $$update plan.tasks set state = 'DONE'$$, '42501');
select test.throws('cannot mark paid', $$update plan.tasks set paid = true$$, '42501');
select test.throws('cannot set focused minutes', $$update plan.tasks set focused_minutes = 600$$, '42501');
select test.throws('blank title', $$insert into plan.tasks (title) values ('   ')$$, '23514');
select test.throws('bad rank', $$insert into plan.tasks (title, rank) values ('x', 'S')$$, '23514');
select test.throws('no calling finish_task', $$select plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c001')$$, '42501');

-- At most 5 subtasks.
insert into plan.subtasks (task_id, title) select '00000000-0000-0000-0000-00000000c001', 'step ' || n from generate_series(1, 5) n;
select test.throws('sixth subtask', $$insert into plan.subtasks (task_id, title) values ('00000000-0000-0000-0000-00000000c001', 'too many')$$, '23514');

-- Habits, goals and the links between them.
insert into plan.habits (id, title, target) values ('00000000-0000-0000-0000-00000000c0c1', 'Stretch', '{"kind":"daily"}');
insert into plan.habit_completions (habit_id, day) values ('00000000-0000-0000-0000-00000000c0c1', '2026-10-19');
select test.throws('one completion a day', $$insert into plan.habit_completions (habit_id, day) values ('00000000-0000-0000-0000-00000000c0c1', '2026-10-19')$$, '23505');
insert into plan.goals (id, title) values ('00000000-0000-0000-0000-00000000c0d1', 'Learn guitar');
insert into plan.milestones (goal_id, title) values ('00000000-0000-0000-0000-00000000c0d1', 'First chord');
insert into plan.goal_links (goal_id, task_id) values ('00000000-0000-0000-0000-00000000c0d1', '00000000-0000-0000-0000-00000000c003');
select test.throws('a link points at a task or a habit', $$insert into plan.goal_links (goal_id) values ('00000000-0000-0000-0000-00000000c0d1')$$, '23514');
insert into plan.rest_days (day) values ('2026-10-20');
insert into plan.focus_sessions (task_id, started_at, duration_minutes, region, outcome) values ('00000000-0000-0000-0000-00000000c001', now(), 25, 'KANTO', 'COMPLETED');
select test.throws('session length', $$insert into plan.focus_sessions (started_at, duration_minutes, region, outcome) values (now(), 0, 'KANTO', 'COMPLETED')$$, '23514');
reset role;

-- Another player sees none of it and cannot attach to it.
select test.as_user('00000000-0000-0000-0000-0000000000c2');
set role authenticated;
select test.eq('bob sees no tasks', (select count(*) from plan.tasks), 0::bigint);
select test.eq('bob sees no lists', (select count(*) from plan.lists), 0::bigint);
select test.eq('bob cannot edit', test.rows($$update plan.tasks set title = 'Hacked'$$), 0::bigint);
select test.throws('bob cannot use alice''s list', $$insert into plan.tasks (title, list_id) values ('x', '00000000-0000-0000-0000-00000000c0a1')$$, '23503');
select test.throws('bob cannot add to alice''s task', $$insert into plan.subtasks (task_id, title) values ('00000000-0000-0000-0000-00000000c001', 'x')$$, '23503');
select test.throws('bob cannot write as alice', $$insert into plan.tasks (user_id, title) values ('00000000-0000-0000-0000-0000000000c1', 'x')$$, '42501');
reset role;

-- finish_task: a young one-off task pays nothing, an older one pays its tier once, a recurring one pays at once.
set role service_role;
select test.eq('too young to pay', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c001'), null::int);
select test.eq('recurring pays at once', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c002'), 2);
select test.eq('a finished task pays nothing again', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c002'), null::int);
reset role;
update plan.tasks set created_at = now() - interval '3 minutes' where id = '00000000-0000-0000-0000-00000000c003';
set role service_role;
select test.eq('E rank pays after 2 minutes', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c003'), 1);
select plan.reopen_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c003');
select test.eq('reopened task is open', (select state from plan.tasks where id = '00000000-0000-0000-0000-00000000c003'), 'OPEN');
select test.eq('reopening never pays twice', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c003'), null::int);
reset role;
update plan.tasks set created_at = now() - interval '11 minutes' where id = '00000000-0000-0000-0000-00000000c001';
set role service_role;
select plan.reopen_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c001');
select test.eq('C rank pays after 10 minutes', plan.finish_task('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-00000000c001'), 3);
select test.throws('unknown task', $$select plan.finish_task('00000000-0000-0000-0000-0000000000c1', gen_random_uuid())$$, 'P0002');
reset role;
