-- Friends, the weekly leaderboard and daily gifts. Everything goes through functions, so a player can only see what a
-- friend chooses to show: trainer name, rank, buddy and this week's points.

create table platform.friendships (
  requester  uuid not null references platform.profiles (user_id) on delete cascade,
  addressee  uuid not null references platform.profiles (user_id) on delete cascade,
  status     text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
-- One row per pair, whichever way round the request went.
create unique index friendships_pair on platform.friendships (least(requester, addressee), greatest(requester, addressee));

create table platform.gifts (
  id         uuid primary key default gen_random_uuid(),
  sender     uuid not null references platform.profiles (user_id) on delete cascade,
  recipient  uuid not null references platform.profiles (user_id) on delete cascade,
  day        date not null default (now() at time zone 'utc')::date,
  item_id    text not null default 'rare-candy',
  amount     int  not null default 3 check (amount between 1 and 10),
  opened_at  timestamptz,
  created_at timestamptz not null default now(),
  check (sender <> recipient),
  -- A friend can be sent one gift a day.
  unique (sender, recipient, day)
);

alter table platform.friendships enable row level security;
alter table platform.gifts enable row level security;
create policy friendships_own on platform.friendships for select to authenticated
  using (requester = (select auth.uid()) or addressee = (select auth.uid()));
create policy gifts_own on platform.gifts for select to authenticated
  using (recipient = (select auth.uid()) or sender = (select auth.uid()));
grant select on platform.friendships, platform.gifts to authenticated;
grant all on platform.friendships, platform.gifts to service_role;

create function platform.are_friends(a uuid, b uuid) returns boolean
  language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from platform.friendships
    where status = 'accepted' and ((requester = a and addressee = b) or (requester = b and addressee = a)))
$$;

-- Sends a friend request by friend code. If that player already asked you, you become friends. Returns the other player's id.
create function platform.add_friend(p_code text) returns uuid
  language plpgsql security definer set search_path = '' as $$
declare
  me uuid := (select auth.uid());
  other uuid;
begin
  if me is null then raise exception 'not signed in' using errcode = 'insufficient_privilege'; end if;
  select user_id into other from platform.profiles where friend_code = upper(btrim(p_code));
  if other is null then raise exception 'no player has that friend code' using errcode = 'no_data_found'; end if;
  if other = me then raise exception 'that is your own friend code'; end if;

  if exists (select 1 from platform.friendships where requester = other and addressee = me) then
    update platform.friendships set status = 'accepted' where requester = other and addressee = me;
  else
    insert into platform.friendships (requester, addressee) values (me, other) on conflict do nothing;
  end if;
  return other;
end $$;

-- Answers a request someone sent you: accept it or turn it down.
create function platform.respond_to_request(p_requester uuid, p_accept boolean) returns void
  language plpgsql security definer set search_path = '' as $$
declare me uuid := (select auth.uid());
begin
  if p_accept then
    update platform.friendships set status = 'accepted' where requester = p_requester and addressee = me and status = 'pending';
  else
    delete from platform.friendships where requester = p_requester and addressee = me and status = 'pending';
  end if;
end $$;

create function platform.remove_friend(p_friend uuid) returns void
  language sql security definer set search_path = '' as $$
  delete from platform.friendships
  where (requester = (select auth.uid()) and addressee = p_friend) or (requester = p_friend and addressee = (select auth.uid()));
$$;

-- Friends and pending requests, with what a friend card shows.
create function platform.friend_cards() returns table (
  user_id uuid, trainer_name text, rank int, status text, incoming boolean,
  buddy_species int, buddy_form text, buddy_shiny boolean, buddy_level int
) language sql stable security definer set search_path = '' as $$
  select pr.user_id, pr.trainer_name, platform.rank_for(pr.trainer_points), f.status, f.addressee = (select auth.uid()),
         b.species_id, b.form_identifier, b.is_shiny, b.level
  from platform.friendships f
  join platform.profiles pr on pr.user_id = case when f.requester = (select auth.uid()) then f.addressee else f.requester end
  left join platform.pokemon b on b.user_id = pr.user_id and b.id = pr.buddy_id and f.status = 'accepted'
  where (select auth.uid()) in (f.requester, f.addressee)
  order by pr.trainer_name
$$;

-- Trainer points earned this week (since Monday, UTC) by you and your friends.
create function platform.weekly_leaderboard() returns table (user_id uuid, trainer_name text, rank int, points int, is_me boolean)
  language sql stable security definer set search_path = '' as $$
  with circle as (
    select (select auth.uid()) as id
    union
    select case when requester = (select auth.uid()) then addressee else requester end
    from platform.friendships where status = 'accepted' and (select auth.uid()) in (requester, addressee)
  )
  select pr.user_id, pr.trainer_name, platform.rank_for(pr.trainer_points),
         coalesce(sum(l.points_delta) filter (where l.created_at >= date_trunc('week', now() at time zone 'utc') at time zone 'utc'), 0)::int,
         pr.user_id = (select auth.uid())
  from circle c
  join platform.profiles pr on pr.user_id = c.id
  left join platform.reward_ledger l on l.user_id = pr.user_id
  group by pr.user_id, pr.trainer_name, pr.trainer_points
  order by 4 desc, pr.trainer_name
$$;

-- A daily gift for a friend.
create function platform.send_gift(p_recipient uuid) returns uuid
  language plpgsql security definer set search_path = '' as $$
declare
  me uuid := (select auth.uid());
  gift uuid;
begin
  if me is null or not platform.are_friends(me, p_recipient) then
    raise exception 'you can only send gifts to friends' using errcode = 'insufficient_privilege';
  end if;
  insert into platform.gifts (sender, recipient) values (me, p_recipient) returning id into gift;
  return gift;
exception when unique_violation then
  raise exception 'you already sent that friend a gift today' using errcode = 'unique_violation';
end $$;

-- Opens a gift sent to you: the items go into your bag (candies stop at the bag limit).
create function platform.open_gift(p_gift uuid) returns jsonb
  language plpgsql security definer set search_path = '' as $$
declare
  me uuid := (select auth.uid());
  g platform.gifts;
begin
  select * into g from platform.gifts where id = p_gift and recipient = me and opened_at is null for update;
  if not found then raise exception 'no such gift' using errcode = 'no_data_found'; end if;
  perform set_config('platform.applying', 'on', true);
  insert into platform.inventory (user_id, item_id, count) values (me, g.item_id, g.amount)
  on conflict (user_id, item_id) do update set count = least(9999, platform.inventory.count + excluded.count);
  update platform.gifts set opened_at = now() where id = g.id;
  update platform.profiles set version = version + 1 where user_id = me;
  return jsonb_build_object('item', g.item_id, 'amount', g.amount);
end $$;

revoke execute on all functions in schema platform from public, anon;
grant execute on function platform.add_friend(text), platform.respond_to_request(uuid, boolean), platform.remove_friend(uuid),
  platform.friend_cards(), platform.weekly_leaderboard(), platform.send_gift(uuid), platform.open_gift(uuid) to authenticated;
grant execute on function platform.are_friends(uuid, uuid) to service_role;
