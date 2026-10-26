-- Training plans are gone (PokéPlan 0.27.0, reward rules version 4): OwnedPokemon no longer has a trainingPlan, so the
-- state no longer carries one. The platform.pokemon.training_plan column stays (writers store '{}') so older reward
-- functions and saves keep working; a later migration can drop it.

-- The player's state as PlayerState JSON, plus the version to hand back to apply_outcome.
create or replace function platform.load_state(p_user uuid) returns jsonb
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
          'personality', p.personality, 'inParty', p.in_party,
          'caughtAtMillis', p.caught_at) order by p.seq)
        from platform.pokemon p where p.user_id = p_user), '[]'),
      'buddyId', pr.buddy_id,
      'inventory', jsonb_build_object('counts', coalesce((
        select jsonb_object_agg(i.item_id, i.count) from platform.inventory i where i.user_id = p_user), '{}')),
      'coins', pr.coins,
      'eggMeter', pr.egg_meter,
      'trainerPoints', pr.trainer_points,
      'caught', to_jsonb(coalesce((select array_agg(c order by c) from unnest(pr.caught) c), '{}')),
      'guardians', to_jsonb(pr.guardian_ids))
  );
end $$;
