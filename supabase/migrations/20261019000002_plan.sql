-- The planner app's schema: tasks, habits, goals, focus sessions and reviews. The rules live in plan-core; the database
-- stores the data and guards what a client must not be able to set (the server clock, completion and payment).

create schema plan;

create table plan.lists (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references platform.profiles (user_id) on delete cascade,
  name       text not null check (char_length(name) between 1 and 60),
  color      text,
  sort_order int  not null default 0,
  archived   boolean not null default false,
  created_at timestamptz not null default now(),
  unique (id, user_id)
);

-- A repeating task. The template's fields are copied into each instance; the recurrence is plan-core's Recurrence as JSON.
create table plan.series (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references platform.profiles (user_id) on delete cascade,
  title       text not null check (char_length(btrim(title)) > 0),
  rank        text not null default 'C' check (rank in ('E', 'D', 'C', 'B', 'A')),
  category    text not null default 'PERSONAL',
  list_id     uuid,
  estimate_minutes int check (estimate_minutes > 0),
  recurrence  jsonb not null,
  ended       boolean not null default false,
  created_at  timestamptz not null default now(),
  unique (id, user_id)
);

create table plan.tasks (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references platform.profiles (user_id) on delete cascade,
  title            text not null check (char_length(btrim(title)) > 0),
  notes            text not null default '',
  rank             text not null default 'C' check (rank in ('E', 'D', 'C', 'B', 'A')),
  category         text not null default 'PERSONAL',
  list_id          uuid,
  -- Wall-clock ("floating") dates and times, so they keep their local time when the time zone changes.
  planned          date,
  planned_time     time,
  due              timestamp,
  estimate_minutes int check (estimate_minutes > 0),
  series_id        uuid,
  evening          boolean not null default false,
  icon             text,
  tutorial         boolean not null default false,
  -- Set by the server only: the clock a task's age is judged by, and how it was finished.
  state            text not null default 'OPEN' check (state in ('OPEN', 'DONE', 'WONT_DO', 'DELETED')),
  created_at       timestamptz not null default now(),
  completed_at     timestamptz,
  deleted_at       timestamptz,
  focused_minutes  int not null default 0 check (focused_minutes >= 0),
  paid             boolean not null default false,
  foreign key (list_id, user_id) references plan.lists (id, user_id) on delete set null (list_id),
  foreign key (series_id, user_id) references plan.series (id, user_id) on delete set null (series_id),
  unique (id, user_id)
);
create index tasks_planned on plan.tasks (user_id, planned) where state = 'OPEN';

create table plan.subtasks (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references platform.profiles (user_id) on delete cascade,
  task_id    uuid not null,
  title      text not null check (char_length(btrim(title)) > 0),
  done       boolean not null default false,
  paid       boolean not null default false,
  created_at timestamptz not null default now(),
  foreign key (task_id, user_id) references plan.tasks (id, user_id) on delete cascade
);

-- A task may have at most 5 subtasks.
create function plan.check_subtask_count() returns trigger language plpgsql as $$
begin
  if (select count(*) from plan.subtasks where task_id = new.task_id) >= 5 then
    raise exception 'a task holds at most 5 subtasks' using errcode = 'check_violation';
  end if;
  return new;
end $$;
create trigger subtasks_limit before insert on plan.subtasks for each row execute function plan.check_subtask_count();

create table plan.habits (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references platform.profiles (user_id) on delete cascade,
  title      text not null check (char_length(btrim(title)) > 0),
  rank       text not null default 'C' check (rank in ('E', 'D', 'C', 'B', 'A')),
  category   text not null default 'HEALTH',
  -- plan-core's HabitTarget as JSON.
  target     jsonb not null,
  created_on date not null default current_date,
  archived   boolean not null default false,
  created_at timestamptz not null default now(),
  unique (id, user_id)
);

create table plan.habit_completions (
  user_id  uuid not null references platform.profiles (user_id) on delete cascade,
  habit_id uuid not null,
  day      date not null,
  primary key (habit_id, day),
  foreign key (habit_id, user_id) references plan.habits (id, user_id) on delete cascade
);

create table plan.goals (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references platform.profiles (user_id) on delete cascade,
  title        text not null check (char_length(btrim(title)) > 0),
  category     text not null default 'PERSONAL',
  target_date  date,
  created_at   timestamptz not null default now(),
  completed_at timestamptz,
  bonus_given  boolean not null default false,
  unique (id, user_id)
);

create table plan.milestones (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references platform.profiles (user_id) on delete cascade,
  goal_id    uuid not null,
  title      text not null check (char_length(btrim(title)) > 0),
  created_at timestamptz not null default now(),
  done_at    timestamptz,
  rewarded   boolean not null default false,
  foreign key (goal_id, user_id) references plan.goals (id, user_id) on delete cascade
);

create table plan.goal_links (
  user_id  uuid not null references platform.profiles (user_id) on delete cascade,
  goal_id  uuid not null,
  task_id  uuid,
  habit_id uuid,
  check ((task_id is null) <> (habit_id is null)),
  foreign key (goal_id, user_id) references plan.goals (id, user_id) on delete cascade,
  foreign key (task_id, user_id) references plan.tasks (id, user_id) on delete cascade,
  foreign key (habit_id, user_id) references plan.habits (id, user_id) on delete cascade
);
create unique index goal_links_task on plan.goal_links (goal_id, task_id) where task_id is not null;
create unique index goal_links_habit on plan.goal_links (goal_id, habit_id) where habit_id is not null;

create table plan.focus_sessions (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references platform.profiles (user_id) on delete cascade,
  task_id         uuid,
  started_at      timestamptz not null,
  duration_minutes int not null check (duration_minutes between 1 and 240),
  deep_focus      boolean not null default false,
  region          text not null,
  outcome         text not null check (outcome in ('COMPLETED', 'LEFT_APP', 'MOVED_PHONE', 'GAVE_UP')),
  paused_seconds  int not null default 0 check (paused_seconds >= 0),
  created_at      timestamptz not null default now(),
  foreign key (task_id, user_id) references plan.tasks (id, user_id) on delete set null (task_id)
);
create index focus_sessions_recent on plan.focus_sessions (user_id, started_at desc);

create table plan.reviews (
  user_id       uuid not null references platform.profiles (user_id) on delete cascade,
  week_start    date not null,
  prompt        text not null,
  note          text,
  carried_forward uuid[] not null default '{}',
  rewarded      boolean not null default false,
  completed_at  timestamptz not null default now(),
  primary key (user_id, week_start)
);

create table plan.rest_days (
  user_id uuid not null references platform.profiles (user_id) on delete cascade,
  day     date not null,
  primary key (user_id, day)
);

-- Row level security: own rows only.
do $$
declare t text;
begin
  foreach t in array array['lists', 'series', 'tasks', 'subtasks', 'habits', 'habit_completions', 'goals', 'milestones',
                           'goal_links', 'focus_sessions', 'reviews', 'rest_days'] loop
    execute format('alter table plan.%I enable row level security', t);
    execute format('alter table plan.%I alter column user_id set default auth.uid()', t);
    execute format($p$create policy %I on plan.%I for all to authenticated
                      using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))$p$, t || '_own', t);
  end loop;
end $$;

-- Clients may write the fields below and nothing else. Everything the server decides (created_at, state, completed_at,
-- deleted_at, paid, focused_minutes, rewarded, bonus_given) is only set by the service role, through the functions further down.
grant usage on schema plan to authenticated, service_role;
grant select, delete on all tables in schema plan to authenticated;
grant insert (id, user_id, name, color, sort_order, archived), update (name, color, sort_order, archived) on plan.lists to authenticated;
grant insert (id, user_id, title, rank, category, list_id, estimate_minutes, recurrence, ended),
      update (title, rank, category, list_id, estimate_minutes, recurrence, ended) on plan.series to authenticated;
grant insert (id, user_id, title, notes, rank, category, list_id, planned, planned_time, due, estimate_minutes, series_id, evening, icon),
      update (title, notes, rank, category, list_id, planned, planned_time, due, estimate_minutes, evening, icon) on plan.tasks to authenticated;
grant insert (id, user_id, task_id, title), update (title, done) on plan.subtasks to authenticated;
grant insert (id, user_id, title, rank, category, target, created_on), update (title, rank, category, target, archived) on plan.habits to authenticated;
grant insert (user_id, habit_id, day) on plan.habit_completions to authenticated;
grant insert (id, user_id, title, category, target_date), update (title, category, target_date) on plan.goals to authenticated;
grant insert (id, user_id, goal_id, title), update (title) on plan.milestones to authenticated;
grant insert (user_id, goal_id, task_id, habit_id) on plan.goal_links to authenticated;
grant insert (id, user_id, task_id, started_at, duration_minutes, deep_focus, region, outcome, paused_seconds) on plan.focus_sessions to authenticated;
grant insert (user_id, week_start, prompt, note, carried_forward), update (note) on plan.reviews to authenticated;
grant insert (user_id, day) on plan.rest_days to authenticated;
grant all on all tables in schema plan to service_role;

-- The state of a finished task, decided on the server clock. Called by the reward function (service role) just before it
-- runs the engine. Returns the rank tier to reward, or null if this completion pays nothing: it already paid, or it is a
-- one-off task younger than its rank's minimum age. Recurring and tutorial tasks are exempt from the minimum age.
create function plan.finish_task(p_user uuid, p_task uuid) returns int
  language plpgsql security definer set search_path = '' as $$
declare
  t plan.tasks;
  min_age int;
  pays boolean;
begin
  select * into t from plan.tasks where id = p_task and user_id = p_user for update;
  if not found then raise exception 'no such task' using errcode = 'no_data_found'; end if;
  if t.state <> 'OPEN' then return null; end if;

  min_age := case t.rank when 'E' then 2 when 'D' then 5 when 'C' then 10 when 'B' then 15 else 20 end;
  pays := not t.paid and (t.tutorial or t.series_id is not null or now() - t.created_at >= make_interval(mins => min_age));

  update plan.tasks set state = 'DONE', completed_at = now(), paid = t.paid or pays where id = t.id;
  return case when pays then case t.rank when 'E' then 1 when 'D' then 2 when 'C' then 3 when 'B' then 4 else 5 end end;
end $$;

-- Reopening never un-pays: the paid flag stays.
create function plan.reopen_task(p_user uuid, p_task uuid) returns void
  language sql security definer set search_path = '' as $$
  update plan.tasks set state = 'OPEN', completed_at = null where id = p_task and user_id = p_user and state in ('DONE', 'WONT_DO');
$$;

revoke execute on all functions in schema plan from public, anon, authenticated;
grant execute on function plan.finish_task(uuid, uuid), plan.reopen_task(uuid, uuid) to service_role;
