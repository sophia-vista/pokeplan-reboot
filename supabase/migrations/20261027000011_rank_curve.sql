-- A new trainer rank curve (PokéPlan 0.28.0, reward rules version 5): quick at first, slower after. Same as
-- TrainerRank.thresholds in pokemon-core; supabase/tests/run.sh checks they match. Points never drop, so nobody loses a rank.
create or replace function platform.rank_thresholds() returns int[]
  language sql immutable parallel safe as $$ select array[0, 50, 150, 325, 650, 1150, 1900, 3000, 5000, 9000] $$;
