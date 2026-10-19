-- The shared platform schema: one trainer, one Pokémon collection, used by every app. See "Platform and apps" in docs/design.md.
-- Game state is only ever changed by platform.apply_outcome (see 20261019000003_state_functions.sql); clients read it and
-- may edit a few cosmetic columns.

create schema platform;

-- Rank thresholds, same as TrainerRank.thresholds in pokemon-core. supabase/tests/run.sh checks they match.
create function platform.rank_thresholds() returns int[]
  language sql immutable parallel safe as $$ select array[0, 625, 1375, 2250, 3250, 4375, 5625, 7125, 8750, 10625] $$;

-- The rank index (0 is the first rank) for some trainer points.
create function platform.rank_for(p_points int) returns int
  language sql immutable parallel safe as $$
  select coalesce(max(i - 1), 0)::int
  from unnest(platform.rank_thresholds()) with ordinality as t(threshold, i)
  where p_points >= threshold
$$;

-- CHECK constraints cannot hold subqueries, so range checks on arrays go through this.
create function platform.all_between(p_values int[], p_low int, p_high int) returns boolean
  language sql immutable parallel safe as $$
  select coalesce(bool_and(v between p_low and p_high), true) from unnest(p_values) v
$$;

create table platform.profiles (
  user_id        uuid primary key references auth.users (id) on delete cascade,
  trainer_name   text not null default 'Trainer' check (char_length(trainer_name) between 1 and 24),
  friend_code    text not null unique default upper(substr(md5(gen_random_uuid()::text), 1, 8)),
  trainer_points int  not null default 0 check (trainer_points >= 0),
  coins          int  not null default 0 check (coins >= 0),
  -- Points towards the next Task Egg; the meter fills at EGG_METER_SIZE (10) and resets.
  egg_meter      int  not null default 0 check (egg_meter >= 0 and egg_meter < 10),
  buddy_id       text,
  -- Every species number ever owned. It never shrinks.
  caught         int[] not null default '{}',
  -- Bumped by every change to the game state; apply_outcome refuses a stale one.
  version        int  not null default 0,
  settings       jsonb not null default '{}',
  created_at     timestamptz not null default now()
);

create table platform.pokemon (
  user_id         uuid not null references platform.profiles (user_id) on delete cascade,
  id              text not null,
  -- Keeps the collection in the order Pokémon were added.
  seq             bigint generated always as identity,
  species_id      int  not null check (species_id > 0),
  form_identifier text not null,
  nickname        text not null check (char_length(nickname) between 1 and 40),
  nature          text not null,
  ability         text not null,
  gender          text check (gender in ('MALE', 'FEMALE')),
  -- Order: hp, attack, defense, spAttack, spDefense, speed.
  ivs             int[] not null check (array_length(ivs, 1) = 6 and platform.all_between(ivs, 0, 31)),
  evs             int[] not null default '{0,0,0,0,0,0}' check (
                    array_length(evs, 1) = 6 and platform.all_between(evs, 0, 252)
                    and evs[1] + evs[2] + evs[3] + evs[4] + evs[5] + evs[6] <= 510),
  level           int  not null default 1 check (level between 1 and 100),
  candy_progress  int  not null default 0 check (candy_progress >= 0),
  total_candies   int  not null default 0 check (total_candies >= 0),
  friendship      int  not null default 70 check (friendship between 0 and 255),
  is_shiny        boolean not null default false,
  held_item       text,
  tera_type       text,
  cosmetic_form   text,
  training_plan   text[] not null default '{}' check (cardinality(training_plan) <= 2),
  personality     int  not null default 0,
  in_party        boolean not null default false,
  caught_at       bigint not null,
  primary key (user_id, id)
);
create index pokemon_order on platform.pokemon (user_id, seq);

-- The buddy must be one of the player's own Pokémon. Checked at commit so a replace-all write can order its statements freely.
alter table platform.profiles add constraint profiles_buddy_fk
  foreign key (user_id, buddy_id) references platform.pokemon (user_id, id)
  on delete set null (buddy_id) deferrable initially deferred;

create table platform.inventory (
  user_id uuid not null references platform.profiles (user_id) on delete cascade,
  item_id text not null,
  count   int  not null check (count > 0),
  primary key (user_id, item_id)
);

-- One row per applied outcome. (user, app, key) makes a retry harmless; the seed and event let the outcome be replayed.
create table platform.reward_ledger (
  id             bigint generated always as identity primary key,
  user_id        uuid not null references platform.profiles (user_id) on delete cascade,
  app            text not null,
  source         text not null,
  idempotency_key text not null,
  seed           int  not null,
  event          jsonb not null,
  rewards        jsonb not null,
  points_delta   int  not null default 0,
  state_version  int  not null,
  created_at     timestamptz not null default now(),
  unique (user_id, app, idempotency_key)
);
create index reward_ledger_recent on platform.reward_ledger (user_id, created_at desc);

-- A player may have at most 6 Pokémon in the party, however they got there. Checked at commit.
create function platform.check_party_size() returns trigger language plpgsql as $$
begin
  if (select count(*) from platform.pokemon where user_id = new.user_id and in_party) > 6 then
    raise exception 'the party holds at most 6 Pokémon' using errcode = 'check_violation';
  end if;
  return null;
end $$;
create constraint trigger pokemon_party_size after insert or update of in_party on platform.pokemon
  deferrable initially deferred for each row when (new.in_party) execute function platform.check_party_size();

-- Edits made outside apply_outcome (renaming, changing the buddy) still bump the version, so a stale outcome cannot undo them.
create function platform.bump_version() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(current_setting('platform.applying', true), '') = 'on' then
    return coalesce(new, old);
  end if;
  if tg_table_name = 'profiles' then
    new.version := old.version + 1;
    return new;
  end if;
  update platform.profiles set version = version + 1 where user_id = coalesce(new.user_id, old.user_id);
  return coalesce(new, old);
end $$;
create trigger profiles_bump before update on platform.profiles for each row execute function platform.bump_version();
create trigger pokemon_bump after update on platform.pokemon for each row execute function platform.bump_version();

-- A profile is created with the account.
create function platform.handle_new_user() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into platform.profiles (user_id) values (new.id) on conflict do nothing;
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function platform.handle_new_user();

-- Row level security: a player sees and edits only their own rows.
alter table platform.profiles enable row level security;
alter table platform.pokemon enable row level security;
alter table platform.inventory enable row level security;
alter table platform.reward_ledger enable row level security;

create policy profiles_own on platform.profiles for select to authenticated using (user_id = (select auth.uid()));
create policy profiles_edit on platform.profiles for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy pokemon_own on platform.pokemon for select to authenticated using (user_id = (select auth.uid()));
create policy pokemon_edit on platform.pokemon for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy inventory_own on platform.inventory for select to authenticated using (user_id = (select auth.uid()));
create policy ledger_own on platform.reward_ledger for select to authenticated using (user_id = (select auth.uid()));

-- Column grants: the game state is read-only to clients except for a few harmless columns.
grant usage on schema platform to authenticated, service_role;
grant select on platform.profiles, platform.pokemon, platform.inventory, platform.reward_ledger to authenticated;
grant update (trainer_name, settings, buddy_id) on platform.profiles to authenticated;
grant update (nickname, in_party, training_plan) on platform.pokemon to authenticated;
grant all on all tables in schema platform to service_role;
grant execute on function platform.rank_for(int), platform.rank_thresholds(), platform.all_between(int[], int, int) to authenticated, service_role;
