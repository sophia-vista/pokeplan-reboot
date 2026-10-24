-- Deleting an account (the reward function deletes the auth user) leaves nothing of the player in any table.
insert into auth.users (id, email) values ('00000000-0000-0000-0000-00000000dd01', 'gone@example.com');
insert into auth.users (id, email) values ('00000000-0000-0000-0000-00000000dd02', 'stays@example.com');

\set golden `cat "$GOLDEN"`
create temp table golden as select :'golden'::jsonb as j;
grant select on golden to public;

set role service_role;
select platform.push_save('00000000-0000-0000-0000-00000000dd01', 0,
  jsonb_build_object('onboarded', true, 'tasks', '[]'::jsonb, 'player', (select (j->>'state')::jsonb from golden)));
select platform.push_save('00000000-0000-0000-0000-00000000dd02', 0,
  jsonb_build_object('onboarded', true, 'tasks', '[]'::jsonb, 'player', (select (j->>'state')::jsonb from golden)));
reset role;
insert into plan.tasks (user_id, title) values ('00000000-0000-0000-0000-00000000dd01', 'Something');
insert into platform.friendships (requester, addressee) values ('00000000-0000-0000-0000-00000000dd02', '00000000-0000-0000-0000-00000000dd01');

-- Every row that mentions the user, in any uuid column of the platform and plan schemas (new tables are covered too).
create function pg_temp.rows_of(uid uuid) returns bigint language plpgsql as $$
declare c record; n bigint; total bigint := 0;
begin
  for c in select table_schema, table_name, column_name from information_schema.columns
           where table_schema in ('platform', 'plan') and data_type = 'uuid' loop
    execute format('select count(*) from %I.%I where %I = $1', c.table_schema, c.table_name, c.column_name) into n using uid;
    total := total + n;
  end loop;
  return total;
end $$;

select test.eq('the player has data before', pg_temp.rows_of('00000000-0000-0000-0000-00000000dd01') > 3, true);
delete from auth.users where id = '00000000-0000-0000-0000-00000000dd01';
select test.eq('nothing is left after deleting the account', pg_temp.rows_of('00000000-0000-0000-0000-00000000dd01'), 0::bigint);
select test.eq('other players keep their data', (select count(*) from platform.pokemon where user_id = '00000000-0000-0000-0000-00000000dd02') > 0, true);
