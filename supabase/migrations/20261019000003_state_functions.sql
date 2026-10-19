-- Loading and saving a player's game state as the reward engine's JSON (PlayerState in pokemon-core). Both functions are
-- for the service role only: the Edge Function loads the state, runs the engine, and saves the outcome.

-- The player's state as PlayerState JSON, plus the version to hand back to apply_outcome.
create function platform.load_state(p_user uuid) returns jsonb
  language plpgsql stable security definer set search_path = '' as $$
declare
  pr platform.profiles;
begin
  select * into pr from platform.profiles where user_id = p_user;
  if not found then raise exception 'no such player' using errcode = 'no_data_found'; end if;

  return jsonb_build_object(
    'version', pr.version,
    'state', jsonb_build_object(
      'pokemon', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', p.id, 'speciesId', p.species_id, 'formIdentifier', p.form_identifier, 'nickname', p.nickname,
          'nature', p.nature, 'ability', p.ability, 'gender', p.gender,
          'ivs', platform.spread_json(p.ivs), 'evs', platform.spread_json(p.evs),
          'level', p.level, 'candyProgress', p.candy_progress, 'totalCandies', p.total_candies, 'friendship', p.friendship,
          'isShiny', p.is_shiny, 'heldItem', p.held_item, 'teraType', p.tera_type, 'cosmeticForm', p.cosmetic_form,
          'trainingPlan', to_jsonb(p.training_plan), 'personality', p.personality, 'inParty', p.in_party,
          'caughtAtMillis', p.caught_at) order by p.seq)
        from platform.pokemon p where p.user_id = p_user), '[]'),
      'buddyId', pr.buddy_id,
      'inventory', jsonb_build_object('counts', coalesce((
        select jsonb_object_agg(i.item_id, i.count) from platform.inventory i where i.user_id = p_user), '{}')),
      'coins', pr.coins,
      'eggMeter', pr.egg_meter,
      'trainerPoints', pr.trainer_points,
      'caught', to_jsonb(coalesce((select array_agg(c order by c) from unnest(pr.caught) c), '{}')))
  );
end $$;

-- StatSpread JSON for an int[6] in the order hp, attack, defense, spAttack, spDefense, speed.
create function platform.spread_json(p_values int[]) returns jsonb
  language sql immutable parallel safe as $$
  select jsonb_build_object('hp', p_values[1], 'attack', p_values[2], 'defense', p_values[3],
                            'spAttack', p_values[4], 'spDefense', p_values[5], 'speed', p_values[6])
$$;

create function platform.spread_array(p_spread jsonb) returns int[]
  language sql immutable parallel safe as $$
  select array[(p_spread->>'hp')::int, (p_spread->>'attack')::int, (p_spread->>'defense')::int,
               (p_spread->>'spAttack')::int, (p_spread->>'spDefense')::int, (p_spread->>'speed')::int]
$$;

/*
 * Saves what the engine decided. p_outcome is the engine's Outcome JSON ({"state": ..., "rewards": [...]}); p_event and
 * p_seed are what produced it, kept so it can be replayed. Returns {"applied": bool, "version": n, "rewards": [...]}.
 *
 *  - Retrying with the same (p_app, p_key) is harmless: nothing changes and the first result comes back with applied = false.
 *  - A stale p_expected_version raises serialization_failure (40001): load the state again and re-run the engine.
 *  - The player can never lose a Pokémon's species from `caught`, lose trainer points, or end with an oversized party.
 */
create function platform.apply_outcome(
  p_user uuid, p_expected_version int, p_app text, p_source text, p_key text, p_seed int, p_event jsonb, p_outcome jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  pr platform.profiles;
  prior platform.reward_ledger;
  s jsonb := p_outcome->'state';
  new_points int := (s->>'trainerPoints')::int;
  new_caught int[];
  new_version int;
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
    trainer_points = new_points, caught = new_caught, version = new_version
  where user_id = p_user;

  -- Check the deferred constraints now, so a bad outcome fails this call instead of surfacing at commit.
  set constraints all immediate;

  insert into platform.reward_ledger (user_id, app, source, idempotency_key, seed, event, rewards, points_delta, state_version)
  values (p_user, p_app, p_source, p_key, p_seed, p_event, p_outcome->'rewards', new_points - pr.trainer_points, new_version);

  return jsonb_build_object('applied', true, 'version', new_version, 'rewards', p_outcome->'rewards');
end $$;

revoke execute on all functions in schema platform from public, anon, authenticated;
grant execute on function platform.rank_for(int), platform.rank_thresholds(), platform.all_between(int[], int, int) to authenticated, service_role;
grant execute on function platform.load_state(uuid), platform.apply_outcome(uuid, int, text, text, text, int, jsonb, jsonb),
  platform.spread_json(int[]), platform.spread_array(jsonb) to service_role;
