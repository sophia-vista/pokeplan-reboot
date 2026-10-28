-- Care's opt-in cloud copy through the reward function (docs/ecosystem.md, section 6): turning sync off deletes it.
-- Returns whether there was a copy to delete.
create function care.delete_save(p_user uuid) returns boolean
  language sql security definer set search_path = '' as $$
  with gone as (delete from care.saves where user_id = p_user returning 1)
  select exists (select 1 from gone)
$$;

revoke execute on function care.delete_save(uuid) from public, anon, authenticated;
grant execute on function care.delete_save(uuid) to service_role;
