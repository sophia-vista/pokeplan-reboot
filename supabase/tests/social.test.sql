-- Friends, the weekly leaderboard and gifts.
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000d1', 'soc-alice@example.com'),
  ('00000000-0000-0000-0000-0000000000d2', 'soc-bob@example.com'),
  ('00000000-0000-0000-0000-0000000000d3', 'soc-carol@example.com');
update platform.profiles set trainer_name = 'Alice' where user_id = '00000000-0000-0000-0000-0000000000d1';
update platform.profiles set trainer_name = 'Bob' where user_id = '00000000-0000-0000-0000-0000000000d2';
update platform.profiles set trainer_name = 'Carol' where user_id = '00000000-0000-0000-0000-0000000000d3';

-- Alice has a buddy and some points this week and last week.
insert into platform.pokemon (user_id, id, species_id, form_identifier, nickname, nature, ability, ivs, caught_at, level, is_shiny)
  values ('00000000-0000-0000-0000-0000000000d1', 'a1', 25, 'pikachu', 'Pika', 'HARDY', 'Static', '{1,2,3,4,5,6}', 0, 12, true);
update platform.profiles set buddy_id = 'a1', trainer_points = 700 where user_id = '00000000-0000-0000-0000-0000000000d1';
insert into platform.reward_ledger (user_id, app, source, idempotency_key, seed, event, rewards, points_delta, state_version, created_at) values
  ('00000000-0000-0000-0000-0000000000d1', 'plan', 't', 'now', 0, '{}', '[]', 40, 1, now()),
  ('00000000-0000-0000-0000-0000000000d1', 'plan', 't', 'old', 0, '{}', '[]', 500, 1, now() - interval '20 days'),
  ('00000000-0000-0000-0000-0000000000d2', 'plan', 't', 'now', 0, '{}', '[]', 10, 1, now());

select friend_code as bobcode from platform.profiles where user_id = '00000000-0000-0000-0000-0000000000d2' \gset
select test.as_user('00000000-0000-0000-0000-0000000000d1');
set role authenticated;
select test.throws('no unknown codes', $$select platform.add_friend('NOSUCHCODE')$$, 'P0002');
select test.throws('not your own code', $$select platform.add_friend((select friend_code from platform.profiles))$$, 'P0001');
select platform.add_friend(lower(:'bobcode'));
select platform.add_friend(lower(:'bobcode'));
select test.eq('request is pending', (select status from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d2'), 'pending');
select test.eq('outgoing', (select incoming from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d2'), false);
select test.throws('gifts need friends', $$select platform.send_gift('00000000-0000-0000-0000-0000000000d2')$$, '42501');
reset role;

-- Friend codes are looked up case-insensitively and Bob answers the request.
select test.as_user('00000000-0000-0000-0000-0000000000d2');
set role authenticated;
select test.eq('incoming', (select incoming from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), true);
select test.eq('no buddy shown before accepting', (select buddy_species from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), null::int);
select platform.respond_to_request('00000000-0000-0000-0000-0000000000d1', true);
select test.eq('accepted', (select status from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), 'accepted');
select test.eq('friend card shows the buddy', (select buddy_species from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), 25);
select test.eq('friend card shows shiny', (select buddy_shiny from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), true);
select test.eq('friend card rank', (select rank from platform.friend_cards() where user_id = '00000000-0000-0000-0000-0000000000d1'), 1);
select test.throws('friendships are not writable', $$delete from platform.friendships$$, '42501');
reset role;

-- Carol is not a friend: she is not on the leaderboard and cannot see the others' cards.
select test.as_user('00000000-0000-0000-0000-0000000000d3');
set role authenticated;
select test.eq('carol has no friends', (select count(*) from platform.friend_cards()), 0::bigint);
select test.eq('carol sees only herself', (select count(*) from platform.weekly_leaderboard()), 1::bigint);
select test.throws('carol cannot gift', $$select platform.send_gift('00000000-0000-0000-0000-0000000000d1')$$, '42501');
reset role;

-- The leaderboard counts this week only, for you and your friends.
select test.as_user('00000000-0000-0000-0000-0000000000d1');
set role authenticated;
select test.eq('leaderboard size', (select count(*) from platform.weekly_leaderboard()), 2::bigint);
select test.eq('leader', (select trainer_name from platform.weekly_leaderboard() limit 1), 'Alice');
select test.eq('this week only', (select points from platform.weekly_leaderboard() where is_me), 40);
select test.eq('friend points', (select points from platform.weekly_leaderboard() where not is_me), 10);

-- A daily gift.
select platform.send_gift('00000000-0000-0000-0000-0000000000d2');
select test.throws('one gift a day', $$select platform.send_gift('00000000-0000-0000-0000-0000000000d2')$$, '23505');
select test.throws('sender cannot open', $$select platform.open_gift((select id from platform.gifts))$$, 'P0002');
reset role;

select version as bob_version from platform.profiles where user_id = '00000000-0000-0000-0000-0000000000d2' \gset
select test.as_user('00000000-0000-0000-0000-0000000000d2');
set role authenticated;
select test.eq('gift opened', (platform.open_gift((select id from platform.gifts where recipient = '00000000-0000-0000-0000-0000000000d2')))->>'amount', '3');
select test.eq('gift in the bag', (select count from platform.inventory), 3);
select test.eq('gift bumps the version', (select version from platform.profiles), :bob_version + 1);
select test.throws('a gift opens once', $$select platform.open_gift((select id from platform.gifts where recipient = '00000000-0000-0000-0000-0000000000d2'))$$, 'P0002');
select platform.remove_friend('00000000-0000-0000-0000-0000000000d1');
select test.eq('unfriended', (select count(*) from platform.friend_cards()), 0::bigint);
reset role;
