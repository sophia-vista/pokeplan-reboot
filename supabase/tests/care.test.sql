-- The Care save (care.saves): opt-in, the player's alone, its own version, and never any game state.
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-000000000ca1', 'care@example.com'),
  ('00000000-0000-0000-0000-000000000ca2', 'care2@example.com');

set role service_role;
select test.eq('nothing uploaded', care.pull_save('00000000-0000-0000-0000-000000000ca1'), '{"version": 0, "data": null}'::jsonb);
select test.eq('first push', care.push_save('00000000-0000-0000-0000-000000000ca1', 0, '{"routines": [{"id": "r1"}]}'), 1);
select test.eq('pulled back', care.pull_save('00000000-0000-0000-0000-000000000ca1')->'data'->'routines', '[{"id": "r1"}]'::jsonb);
select test.eq('second push', care.push_save('00000000-0000-0000-0000-000000000ca1', 1, '{"routines": []}'), 2);
select test.throws('stale push', $$select care.push_save('00000000-0000-0000-0000-000000000ca1', 1, '{}')$$, '40001');
select test.throws('a first push must expect 0', $$select care.push_save('00000000-0000-0000-0000-000000000ca2', 3, '{}')$$, '40001');
select test.throws('no game state in Care', $$select care.push_save('00000000-0000-0000-0000-000000000ca2', 0, '{"player": {}}')$$, '23514');
select test.eq('Care never touches the game', (select version from platform.profiles where user_id = '00000000-0000-0000-0000-000000000ca1'), 0);
select care.push_save('00000000-0000-0000-0000-000000000ca2', 0, '{"moods": [{"mood": "LOW"}]}');
-- Turning sync off through the reward function deletes the copy; the next upload starts again at version 1.
select test.eq('delete_save removes it', care.delete_save('00000000-0000-0000-0000-000000000ca2'), true);
select test.eq('nothing left to delete', care.delete_save('00000000-0000-0000-0000-000000000ca2'), false);
select test.eq('uploads again from 0', care.push_save('00000000-0000-0000-0000-000000000ca2', 0, '{"moods": []}'), 1);
reset role;

-- A player reads and deletes only their own copy, and cannot write it or call the functions.
select test.as_user('00000000-0000-0000-0000-000000000ca1');
set role authenticated;
select test.eq('own Care save only', (select count(*) from care.saves), 1::bigint);
select test.throws('no direct writes', $$update care.saves set data = '{}'$$, '42501');
select test.throws('no calling push_save', $$select care.push_save('00000000-0000-0000-0000-000000000ca1', 2, '{}')$$, '42501');
select test.throws('no calling delete_save', $$select care.delete_save('00000000-0000-0000-0000-000000000ca2')$$, '42501');
select test.throws('no calling pull_save', $$select care.pull_save('00000000-0000-0000-0000-000000000ca2')$$, '42501');
select test.eq('cannot delete another player''s', test.rows($$delete from care.saves where user_id = '00000000-0000-0000-0000-000000000ca2'$$), 0::bigint);
select test.eq('turning sync off deletes the copy', test.rows($$delete from care.saves$$), 1::bigint);
reset role;
set role anon;
select test.throws('anonymous sees nothing', $$select count(*) from care.saves$$, '42501');
reset role;

-- Deleting the account removes the Care copy too.
delete from auth.users where id = '00000000-0000-0000-0000-000000000ca2';
select test.eq('gone with the account', (select count(*) from care.saves where user_id = '00000000-0000-0000-0000-000000000ca2'), 0::bigint);
