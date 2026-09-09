begin;

select plan(47);

select has_table('public', 'profiles', 'profiles exists');
select has_table('public', 'user_preferences', 'user_preferences exists');
select has_table('public', 'vaults', 'vaults exists');
select has_table('public', 'vault_key_envelopes', 'vault_key_envelopes exists');
select has_table('public', 'vault_items', 'vault_items exists');
select has_table('public', 'security_events', 'security_events exists');

select is(relrowsecurity, true, 'profiles has RLS enabled')
from pg_class where oid = 'public.profiles'::regclass;
select is(relrowsecurity, true, 'user_preferences has RLS enabled')
from pg_class where oid = 'public.user_preferences'::regclass;
select is(relrowsecurity, true, 'vaults has RLS enabled')
from pg_class where oid = 'public.vaults'::regclass;
select is(relrowsecurity, true, 'vault_key_envelopes has RLS enabled')
from pg_class where oid = 'public.vault_key_envelopes'::regclass;
select is(relrowsecurity, true, 'vault_items has RLS enabled')
from pg_class where oid = 'public.vault_items'::regclass;
select is(relrowsecurity, true, 'security_events has RLS enabled')
from pg_class where oid = 'public.security_events'::regclass;

select is(relforcerowsecurity, true, 'profiles forces RLS')
from pg_class where oid = 'public.profiles'::regclass;
select is(relforcerowsecurity, true, 'user_preferences forces RLS')
from pg_class where oid = 'public.user_preferences'::regclass;
select is(relforcerowsecurity, true, 'vaults forces RLS')
from pg_class where oid = 'public.vaults'::regclass;
select is(relforcerowsecurity, true, 'vault_key_envelopes forces RLS')
from pg_class where oid = 'public.vault_key_envelopes'::regclass;
select is(relforcerowsecurity, true, 'vault_items forces RLS')
from pg_class where oid = 'public.vault_items'::regclass;
select is(relforcerowsecurity, true, 'security_events forces RLS')
from pg_class where oid = 'public.security_events'::regclass;

select is(
  has_table_privilege('anon', 'public.profiles', 'select'),
  false,
  'anon has no profile select grant'
);
select is(
  has_table_privilege('anon', 'public.vault_items', 'select'),
  false,
  'anon has no vault item select grant'
);
select is(
  has_table_privilege('authenticated', 'public.profiles', 'select'),
  true,
  'authenticated has the required profile select grant'
);
select is(
  has_table_privilege('authenticated', 'public.security_events', 'insert'),
  false,
  'authenticated cannot forge security events'
);
select is(
  has_column_privilege('authenticated', 'public.vaults', 'owner_user_id', 'update'),
  false,
  'authenticated cannot update vault ownership'
);

select has_function(
  'public',
  'prevent_last_verified_totp_factor',
  'last verified TOTP protection function exists'
);
select has_trigger(
  'auth',
  'mfa_factors',
  'keep_last_verified_totp_factor',
  'last verified TOTP protection trigger exists'
);

insert into auth.users (
  instance_id,
  id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at
) values
  (
    '00000000-0000-0000-0000-000000000000',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'authenticated',
    'authenticated',
    'user-a@mykeys.test',
    'not-a-real-password-hash',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{}'::jsonb,
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'authenticated',
    'authenticated',
    'user-b@mykeys.test',
    'not-a-real-password-hash',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{}'::jsonb,
    now(),
    now()
  );

insert into public.vaults (id, owner_user_id) values
  ('a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
  ('b0000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');

insert into public.vault_items (
  id,
  vault_id,
  encrypted_payload,
  nonce
) values
  (
    'a1000000-0000-4000-8000-000000000001',
    'a0000000-0000-4000-8000-000000000001',
    decode(repeat('aa', 17), 'hex'),
    decode(repeat('01', 12), 'hex')
  ),
  (
    'b1000000-0000-4000-8000-000000000001',
    'b0000000-0000-4000-8000-000000000001',
    decode(repeat('bb', 17), 'hex'),
    decode(repeat('02', 12), 'hex')
  );

insert into auth.mfa_factors (
  id,
  user_id,
  friendly_name,
  factor_type,
  status,
  created_at,
  updated_at,
  secret
) values (
  'a2000000-0000-4000-8000-000000000001',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  'Principal',
  'totp',
  'verified',
  now(),
  now(),
  'TESTONLY'
);

select throws_ok(
  $$delete from auth.mfa_factors
    where id = 'a2000000-0000-4000-8000-000000000001'$$,
  '23514',
  'MYKEYS_LAST_VERIFIED_TOTP_FACTOR',
  'database refuses to delete the last verified TOTP factor'
);

insert into auth.mfa_factors (
  id,
  user_id,
  friendly_name,
  factor_type,
  status,
  created_at,
  updated_at,
  secret
) values (
  'a2000000-0000-4000-8000-000000000002',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  'Respaldo',
  'totp',
  'verified',
  now(),
  now(),
  'TESTONLYBACKUP'
);

select lives_ok(
  $$delete from auth.mfa_factors
    where id = 'a2000000-0000-4000-8000-000000000001'$$,
  'database allows removing one verified TOTP when a backup remains'
);

set local role anon;
set local request.jwt.claims = '{"role":"anon","aal":"aal1"}';
select throws_ok(
  $$select * from public.profiles$$,
  '42501',
  null,
  'anon cannot query private profiles'
);
reset role;

set local role authenticated;
set local request.jwt.claims =
  '{"sub":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","role":"authenticated","aal":"aal1"}';
select results_eq(
  $$select count(*)::bigint from public.profiles$$,
  array[0::bigint],
  'AAL1 cannot read its own profile'
);
select results_eq(
  $$select count(*)::bigint from public.vaults$$,
  array[0::bigint],
  'AAL1 cannot read its own vault'
);
select throws_ok(
  $$insert into public.vaults (id, owner_user_id) values (
    'a0000000-0000-4000-8000-000000000002',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
  )$$,
  '42501',
  null,
  'AAL1 cannot create a private vault'
);

set local request.jwt.claims =
  '{"sub":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","role":"authenticated","aal":"aal2"}';
select results_eq(
  $$select count(*)::bigint from public.profiles$$,
  array[1::bigint],
  'AAL2 user A can read its own profile'
);
select results_eq(
  $$select count(*)::bigint from public.vaults$$,
  array[1::bigint],
  'AAL2 user A sees exactly its own vault'
);
select results_eq(
  $$select count(*)::bigint from public.vault_items$$,
  array[1::bigint],
  'AAL2 user A sees exactly its own item'
);
select results_eq(
  $$select count(*)::bigint from public.vaults
    where id = 'b0000000-0000-4000-8000-000000000001'$$,
  array[0::bigint],
  'user A cannot guess and read user B vault UUID'
);
select results_eq(
  $$select count(*)::bigint from public.vault_items
    where id = 'b1000000-0000-4000-8000-000000000001'$$,
  array[0::bigint],
  'user A cannot guess and read user B item UUID'
);
select throws_ok(
  $$insert into public.vaults (id, owner_user_id) values (
    'a0000000-0000-4000-8000-000000000003',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
  )$$,
  '42501',
  null,
  'user A cannot forge user B as vault owner'
);
select throws_ok(
  $$insert into public.vault_items (id, vault_id, encrypted_payload, nonce) values (
    'a1000000-0000-4000-8000-000000000003',
    'b0000000-0000-4000-8000-000000000001',
    decode(repeat('cc', 17), 'hex'),
    decode(repeat('03', 12), 'hex')
  )$$,
  '42501',
  null,
  'user A cannot insert an item into user B vault'
);
select results_eq(
  $$update public.vault_items
    set revision = revision + 1
    where id = 'b1000000-0000-4000-8000-000000000001'
    returning id$$,
  $$select id from public.vault_items where false$$,
  'user A cannot update user B item'
);
select results_eq(
  $$delete from public.vault_items
    where id = 'b1000000-0000-4000-8000-000000000001'
    returning id$$,
  $$select id from public.vault_items where false$$,
  'user A cannot delete user B item'
);
select throws_ok(
  $$update public.vaults
    set owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
    where id = 'a0000000-0000-4000-8000-000000000001'$$,
  '42501',
  null,
  'column grants block changing a vault owner'
);
select throws_ok(
  $$update public.vault_items
    set vault_id = 'b0000000-0000-4000-8000-000000000001'
    where id = 'a1000000-0000-4000-8000-000000000001'$$,
  '42501',
  null,
  'column grants block moving an item to another vault'
);
select results_eq(
  $$select count(*)::bigint from public.security_events$$,
  array[1::bigint],
  'user A sees only its own server-created security event'
);

set local request.jwt.claims =
  '{"sub":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","role":"authenticated","aal":"aal2"}';
select results_eq(
  $$select count(*)::bigint from public.vaults$$,
  array[1::bigint],
  'AAL2 user B sees exactly its own vault'
);
select results_eq(
  $$select count(*)::bigint from public.vault_items$$,
  array[1::bigint],
  'AAL2 user B sees exactly its own item'
);
select results_eq(
  $$select count(*)::bigint from public.profiles
    where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
  array[0::bigint],
  'user B cannot read user A profile by UUID'
);
reset role;

select is(
  (
    select count(*)::bigint
    from pg_policies
    where schemaname = 'public'
      and policyname like '%require_aal2'
  ),
  6::bigint,
  'every private table has an explicit AAL2 policy'
);

select * from finish();
rollback;
