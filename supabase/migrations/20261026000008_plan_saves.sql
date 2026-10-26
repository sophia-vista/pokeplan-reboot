-- Platform tidy before a second module (docs/ecosystem.md, sections 7 and 8):
--  1. The cloud save is Plan's own data (task titles, notes, settings), so it moves from platform.cloud_saves to plan.saves
--     and loses its copy of the game state, which always comes from the shared tables anyway. `platform` keeps game state only.
--  2. The reward rules are versioned: every ledger row records the rules version (RewardRules.VERSION in pokemon-core) that
--     produced it, so an outcome can be replayed with the rules it was made under. 0 means before versioning, or unknown.

create table plan.saves (
  user_id    uuid primary key references platform.profiles (user_id) on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);

alter table plan.saves enable row level security;
create policy saves_own on plan.saves for select to authenticated using (user_id = (select auth.uid()));
grant select on plan.saves to authenticated;
grant all on plan.saves to service_role;

insert into plan.saves (user_id, data, updated_at) select user_id, data - 'player', updated_at from platform.cloud_saves;
drop function platform.pull_save(uuid);
drop function platform.push_save(uuid, int, jsonb);
drop table platform.cloud_saves;

alter table platform.reward_ledger add column rules_version int not null default 0 check (rules_version >= 0);

/*
 * Saves what the engine decided, as before (20261019000005_guardians.sql), and records p_rules_version on the ledger row.
 * p_rules_version defaults to 0 so a reward function deployed before this migration keeps working until it is redeployed.
 *
 *  - Retrying with the same (p_app, p_key) is harmless: nothing changes and the first result comes back with applied = false.
 *  - A stale p_expected_version raises serialization_failure (40001): load the state again and re-run the engine.
 *  - The player can never lose a Pokémon's species from `caught`, lose trainer points, or end with an oversized party.
 */
drop function platform.apply_outcome(uuid, int, text, text, text, int, jsonb, jsonb);
create function platform.apply_outcome(
  p_user uuid, p_expected_version int, p_app text, p_source text, p_key text, p_seed int, p_event jsonb, p_outcome jsonb,
  p_rules_version int default 0
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  pr platform.profiles;
  prior platform.reward_ledger;
  s jsonb := p_outcome->'state';
  new_points int := (s->>'trainerPoints')::int;
  new_caught int[];
  new_version int;
  new_guardians text[];
begin
  select * into pr from platform.profiles where user_id = p_user for update;
  if not found then raise exception 'no such player' using errcode = 'no_data_found'; end if;

  select * into prior from platform.reward_ledger where user_id = p_user and app = p_app and idempotency_key = p_key;
  if found then
    return jsonb_build_object('applied', false, 'version', pr.version, 'rewards', prior.rewards);
  end if;

  if pr.version <> p_expected_version then
    raise exception 'state has changed (expected version %, found %)', p_expected_version, pr.version
      using errcode = 'serialization_failure';
  end if;
  if new_points < pr.trainer_points then raise exception 'trainer points cannot go down'; end if;

  select coalesce(array_agg(c order by c), '{}') into new_caught from jsonb_array_elements_text(s->'caught') c;
  if not (pr.caught <@ new_caught) then raise exception 'caught species cannot be removed'; end if;
  new_guardians := coalesce((select array_agg(g) from jsonb_array_elements_text(coalesce(s->'guardians', '[]'::jsonb)) g), '{}');
  if cardinality(new_guardians) > platform.guardian_slots(platform.rank_for(new_points)) then
    raise exception 'this rank has % Guardian slots', platform.guardian_slots(platform.rank_for(new_points)) using errcode = 'check_violation';
  end if;

  perform set_config('platform.applying', 'on', true);
  new_version := pr.version + 1;

  -- Pokémon: write the new list, then drop the ones that are gone (transferred).
  insert into platform.pokemon (
    user_id, id, species_id, form_identifier, nickname, nature, ability, gender, ivs, evs, level, candy_progress,
    total_candies, friendship, is_shiny, held_item, tera_type, cosmetic_form, training_plan, personality, in_party, caught_at)
  select p_user, x->>'id', (x->>'speciesId')::int, x->>'formIdentifier', x->>'nickname', x->>'nature', x->>'ability',
         x->>'gender', platform.spread_array(x->'ivs'), platform.spread_array(x->'evs'), (x->>'level')::int,
         (x->>'candyProgress')::int, (x->>'totalCandies')::int, (x->>'friendship')::int, (x->>'isShiny')::boolean,
         x->>'heldItem', x->>'teraType', x->>'cosmeticForm',
         coalesce((select array_agg(v) from jsonb_array_elements_text(x->'trainingPlan') v), '{}'),
         (x->>'personality')::int, (x->>'inParty')::boolean, (x->>'caughtAtMillis')::bigint
  from jsonb_array_elements(s->'pokemon') x
  on conflict (user_id, id) do update set
    species_id = excluded.species_id, form_identifier = excluded.form_identifier, nickname = excluded.nickname,
    nature = excluded.nature, ability = excluded.ability, gender = excluded.gender, ivs = excluded.ivs, evs = excluded.evs,
    level = excluded.level, candy_progress = excluded.candy_progress, total_candies = excluded.total_candies,
    friendship = excluded.friendship, is_shiny = excluded.is_shiny, held_item = excluded.held_item,
    tera_type = excluded.tera_type, cosmetic_form = excluded.cosmetic_form, training_plan = excluded.training_plan,
    personality = excluded.personality, in_party = excluded.in_party, caught_at = excluded.caught_at;

  delete from platform.pokemon
  where user_id = p_user and id not in (select x->>'id' from jsonb_array_elements(s->'pokemon') x);

  -- Inventory: replace.
  delete from platform.inventory where user_id = p_user;
  insert into platform.inventory (user_id, item_id, count)
  select p_user, key, value::int from jsonb_each_text(s->'inventory'->'counts') where value::int > 0;

  update platform.profiles set
    buddy_id = s->>'buddyId', coins = (s->>'coins')::int, egg_meter = (s->>'eggMeter')::int,
    trainer_points = new_points, caught = new_caught, guardian_ids = new_guardians, version = new_version
  where user_id = p_user;

  -- Check the deferred constraints now, so a bad outcome fails this call instead of surfacing at commit.
  set constraints all immediate;

  insert into platform.reward_ledger (user_id, app, source, idempotency_key, seed, event, rewards, points_delta, state_version, rules_version)
  values (p_user, p_app, p_source, p_key, p_seed, p_event, p_outcome->'rewards', new_points - pr.trainer_points, new_version, p_rules_version);

  return jsonb_build_object('applied', true, 'version', new_version, 'rewards', p_outcome->'rewards');
end $$;

-- The player's save: {"version": n, "data": {...}} with `data.player` taken from the shared tables, or version only when
-- nothing has been uploaded yet.
create function plan.pull_save(p_user uuid) returns jsonb
  language plpgsql stable security definer set search_path = '' as $$
declare
  loaded jsonb := platform.load_state(p_user);
  doc jsonb;
begin
  select data into doc from plan.saves where user_id = p_user;
  if doc is null then
    return jsonb_build_object('version', (loaded->>'version')::int, 'data', null);
  end if;
  return jsonb_build_object('version', (loaded->>'version')::int, 'data', doc || jsonb_build_object('player', loaded->'state'));
end $$;

-- Saves a document. p_expected_version must be the version the client last saw (stale: serialization_failure, 40001).
-- The player part goes through apply_outcome, so points cannot drop, caught species cannot vanish, the party and Guardian
-- slots are checked; the rest is stored without it. p_rules_version is the rules the client's engine ran. Returns the new version.
create function plan.push_save(p_user uuid, p_expected_version int, p_data jsonb, p_rules_version int default 0) returns int
  language plpgsql security definer set search_path = '' as $$
declare
  result jsonb;
begin
  result := platform.apply_outcome(
    p_user, p_expected_version, 'sync', 'snapshot', 'sync-' || gen_random_uuid()::text, 0, '{}',
    jsonb_build_object('state', p_data->'player', 'rewards', '[]'::jsonb), p_rules_version);
  insert into plan.saves (user_id, data) values (p_user, p_data - 'player')
  on conflict (user_id) do update set data = excluded.data, updated_at = now();
  return (result->>'version')::int;
end $$;

-- Until the reward function is redeployed it still calls platform.pull_save and platform.push_save: keep them as thin
-- wrappers so the save keeps working in between. Drop them in a later migration.
create function platform.pull_save(p_user uuid) returns jsonb
  language sql stable security definer set search_path = '' as $$ select plan.pull_save(p_user) $$;
create function platform.push_save(p_user uuid, p_expected_version int, p_data jsonb) returns int
  language sql security definer set search_path = '' as $$ select plan.push_save(p_user, p_expected_version, p_data) $$;

revoke execute on function plan.pull_save(uuid), plan.push_save(uuid, int, jsonb, int),
  platform.pull_save(uuid), platform.push_save(uuid, int, jsonb),
  platform.apply_outcome(uuid, int, text, text, text, int, jsonb, jsonb, int) from public, anon, authenticated;
grant execute on function plan.pull_save(uuid), plan.push_save(uuid, int, jsonb, int),
  platform.pull_save(uuid), platform.push_save(uuid, int, jsonb),
  platform.apply_outcome(uuid, int, text, text, text, int, jsonb, jsonb, int) to service_role;
