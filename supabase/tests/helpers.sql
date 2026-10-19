-- Small assertions for the SQL tests.
create schema test;
grant usage on schema test to public;

create function test.eq(label text, got anyelement, want anyelement) returns void language plpgsql as $$
begin
  if got is distinct from want then
    raise exception 'FAIL %: got %, wanted %', label, got, want;
  end if;
end $$;

-- Runs some SQL and checks that it fails with the given SQLSTATE.
create function test.throws(label text, stmt text, want_state text) returns void language plpgsql as $$
begin
  execute stmt;
  raise exception 'FAIL %: expected error %, but it succeeded', label, want_state;
exception when others then
  if sqlstate = 'P0001' and sqlerrm like 'FAIL %' then raise; end if;
  if sqlstate <> want_state then
    raise exception 'FAIL %: expected %, got % (%)', label, want_state, sqlstate, sqlerrm;
  end if;
end $$;

-- Runs some SQL and returns how many rows it changed.
create function test.rows(stmt text) returns bigint language plpgsql as $$
declare n bigint;
begin
  execute stmt;
  get diagnostics n = row_count;
  return n;
end $$;

create function test.as_user(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', uid::text, false);
end $$;
grant execute on all functions in schema test to public;
