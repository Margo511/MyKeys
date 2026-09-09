-- My Keys - Phase 2: private schema, least-privilege grants and mandatory AAL2 RLS.

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text null,
  notifications_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_display_name_length
    check (display_name is null or char_length(display_name) between 1 and 80)
);

create table public.user_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  theme text not null default 'system',
  locale text not null default 'es-ES',
  auto_lock_seconds integer not null default 300,
  clipboard_clear_seconds integer null default 30,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint user_preferences_theme
    check (theme in ('system', 'light', 'dark')),
  constraint user_preferences_locale_length
    check (char_length(locale) between 2 and 16),
  constraint user_preferences_auto_lock
    check (auto_lock_seconds in (0, 60, 300, 900, 1800)),
  constraint user_preferences_clipboard_clear
    check (clipboard_clear_seconds is null or clipboard_clear_seconds in (15, 30, 60))
);

create table public.vaults (
  id uuid primary key,
  owner_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade,
  vault_type text not null default 'personal',
  encrypted_metadata bytea null,
  metadata_nonce bytea null,
  crypto_version smallint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint vaults_type check (vault_type = 'personal'),
  constraint vaults_metadata_nonce_length
    check (metadata_nonce is null or octet_length(metadata_nonce) = 12),
  constraint vaults_crypto_version check (crypto_version > 0),
  constraint vaults_metadata_pair
    check ((encrypted_metadata is null) = (metadata_nonce is null))
);

create unique index vaults_one_personal_per_owner
  on public.vaults(owner_user_id)
  where vault_type = 'personal';

create table public.vault_key_envelopes (
  id uuid primary key,
  vault_id uuid not null references public.vaults(id) on delete cascade,
  wrapper_type text not null,
  state text not null default 'pending',
  wrapped_dek bytea not null,
  nonce bytea not null,
  kdf_salt bytea not null,
  kdf_algorithm text not null,
  kdf_parameters jsonb not null,
  cipher_algorithm text not null default 'aes-256-gcm',
  crypto_version smallint not null default 1,
  created_at timestamptz not null default now(),
  activated_at timestamptz null,
  retired_at timestamptz null,
  constraint vault_key_envelopes_wrapper_type
    check (wrapper_type in ('master_password', 'recovery_key')),
  constraint vault_key_envelopes_state
    check (state in ('pending', 'active', 'retired')),
  constraint vault_key_envelopes_wrapped_dek_length
    check (octet_length(wrapped_dek) between 33 and 256),
  constraint vault_key_envelopes_nonce_length check (octet_length(nonce) = 12),
  constraint vault_key_envelopes_salt_length
    check (octet_length(kdf_salt) between 16 and 32),
  constraint vault_key_envelopes_kdf
    check (kdf_algorithm in ('argon2id-v1', 'hkdf-sha256-v1')),
  constraint vault_key_envelopes_cipher check (cipher_algorithm = 'aes-256-gcm'),
  constraint vault_key_envelopes_crypto_version check (crypto_version > 0),
  constraint vault_key_envelopes_state_timestamps check (
    (state = 'pending' and activated_at is null and retired_at is null)
    or (state = 'active' and activated_at is not null and retired_at is null)
    or (state = 'retired' and activated_at is not null and retired_at is not null)
  ),
  constraint vault_key_envelopes_kdf_parameters check (
    (
      kdf_algorithm = 'argon2id-v1'
      and jsonb_typeof(kdf_parameters) = 'object'
      and (kdf_parameters ->> 'memory_kib')::integer between 65536 and 1048576
      and (kdf_parameters ->> 'iterations')::integer between 3 and 10
      and (kdf_parameters ->> 'parallelism')::integer between 1 and 16
      and (kdf_parameters ->> 'output_bytes')::integer = 32
    )
    or (
      kdf_algorithm = 'hkdf-sha256-v1'
      and jsonb_typeof(kdf_parameters) = 'object'
      and (kdf_parameters ->> 'output_bytes')::integer = 32
      and (kdf_parameters ->> 'info_version')::integer = 1
    )
  )
);

create index vault_key_envelopes_vault_state
  on public.vault_key_envelopes(vault_id, wrapper_type, state);

create unique index vault_key_envelopes_one_active_type
  on public.vault_key_envelopes(vault_id, wrapper_type)
  where state = 'active';

create table public.vault_items (
  id uuid primary key,
  vault_id uuid not null references public.vaults(id) on delete cascade,
  encrypted_payload bytea not null,
  nonce bytea not null,
  cipher_algorithm text not null default 'aes-256-gcm',
  crypto_version smallint not null default 1,
  payload_schema_version smallint not null default 1,
  revision bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz null,
  constraint vault_items_payload_length
    check (octet_length(encrypted_payload) between 17 and 65536),
  constraint vault_items_nonce_length check (octet_length(nonce) = 12),
  constraint vault_items_cipher check (cipher_algorithm = 'aes-256-gcm'),
  constraint vault_items_crypto_version check (crypto_version > 0),
  constraint vault_items_payload_schema_version check (payload_schema_version > 0),
  constraint vault_items_revision check (revision > 0)
);

create index vault_items_active_sync
  on public.vault_items(vault_id, updated_at, id)
  where deleted_at is null;

create index vault_items_trash
  on public.vault_items(vault_id, deleted_at, id)
  where deleted_at is not null;

create table public.security_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null,
  actor_session_id uuid null,
  device_label text null,
  metadata jsonb not null default '{}'::jsonb,
  critical boolean not null default false,
  occurred_at timestamptz not null default now(),
  constraint security_events_type check (event_type in (
    'ACCOUNT_CREATED', 'EMAIL_VERIFIED', 'LOGIN_SUCCESS', 'LOGIN_FAILED',
    'MFA_ENABLED', 'MFA_DISABLED', 'ACCESS_PASSWORD_CHANGED',
    'VAULT_MASTER_PASSWORD_CHANGED', 'VAULT_ITEM_CREATED',
    'VAULT_ITEM_UPDATED', 'VAULT_ITEM_DELETED', 'VAULT_ITEM_RESTORED',
    'RECOVERY_USED', 'RECOVERY_ROTATED', 'SESSIONS_REVOKED', 'ACCOUNT_DELETED'
  )),
  constraint security_events_device_label_length
    check (device_label is null or char_length(device_label) <= 120),
  constraint security_events_metadata_object check (jsonb_typeof(metadata) = 'object')
);

create index security_events_user_time
  on public.security_events(user_id, occurred_at desc, id desc);

create function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger user_preferences_set_updated_at
before update on public.user_preferences
for each row execute function public.set_updated_at();

create trigger vaults_set_updated_at
before update on public.vaults
for each row execute function public.set_updated_at();

create trigger vault_items_set_updated_at
before update on public.vault_items
for each row execute function public.set_updated_at();

create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id);
  insert into public.user_preferences (user_id) values (new.id);
  insert into public.security_events (user_id, event_type, critical)
  values (new.id, 'ACCOUNT_CREATED', true);
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- GoTrue allows a verified factor to be unenrolled at AAL2. The product policy is
-- stricter: an existing account must retain at least one verified TOTP factor.
-- The auth.users existence check keeps account deletion/cascade cleanup possible.
create function public.prevent_last_verified_totp_factor()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if old.factor_type <> 'totp' or old.status <> 'verified' then
    return case when tg_op = 'DELETE' then old else new end;
  end if;

  if tg_op = 'UPDATE' and new.status = 'verified' then
    return new;
  end if;

  if exists (select 1 from auth.users where id = old.user_id)
    and not exists (
      select 1
      from auth.mfa_factors
      where user_id = old.user_id
        and id <> old.id
        and factor_type = 'totp'
        and status = 'verified'
    ) then
    raise exception using
      errcode = '23514',
      message = 'MYKEYS_LAST_VERIFIED_TOTP_FACTOR';
  end if;

  return case when tg_op = 'DELETE' then old else new end;
end;
$$;

create trigger keep_last_verified_totp_factor
before delete or update of status on auth.mfa_factors
for each row execute function public.prevent_last_verified_totp_factor();

-- Treat every row in these tables as private, including profile and preferences.
alter table public.profiles enable row level security;
alter table public.profiles force row level security;
alter table public.user_preferences enable row level security;
alter table public.user_preferences force row level security;
alter table public.vaults enable row level security;
alter table public.vaults force row level security;
alter table public.vault_key_envelopes enable row level security;
alter table public.vault_key_envelopes force row level security;
alter table public.vault_items enable row level security;
alter table public.vault_items force row level security;
alter table public.security_events enable row level security;
alter table public.security_events force row level security;

-- Restrictive policies never grant access by themselves; ownership policies below do.
create policy profiles_require_aal2 on public.profiles
  as restrictive for all to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2')
  with check ((select auth.jwt() ->> 'aal') = 'aal2');
create policy profiles_select_own on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy profiles_update_own on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create policy user_preferences_require_aal2 on public.user_preferences
  as restrictive for all to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2')
  with check ((select auth.jwt() ->> 'aal') = 'aal2');
create policy user_preferences_select_own on public.user_preferences
  for select to authenticated using ((select auth.uid()) = user_id);
create policy user_preferences_update_own on public.user_preferences
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy vaults_require_aal2 on public.vaults
  as restrictive for all to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2')
  with check ((select auth.jwt() ->> 'aal') = 'aal2');
create policy vaults_select_own on public.vaults
  for select to authenticated using ((select auth.uid()) = owner_user_id);
create policy vaults_insert_own on public.vaults
  for insert to authenticated with check ((select auth.uid()) = owner_user_id);
create policy vaults_update_own on public.vaults
  for update to authenticated
  using ((select auth.uid()) = owner_user_id)
  with check ((select auth.uid()) = owner_user_id);

create policy vault_key_envelopes_require_aal2 on public.vault_key_envelopes
  as restrictive for all to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2')
  with check ((select auth.jwt() ->> 'aal') = 'aal2');
create policy vault_key_envelopes_select_own on public.vault_key_envelopes
  for select to authenticated using (exists (
    select 1 from public.vaults
    where vaults.id = vault_key_envelopes.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));
create policy vault_key_envelopes_insert_own on public.vault_key_envelopes
  for insert to authenticated with check (exists (
    select 1 from public.vaults
    where vaults.id = vault_key_envelopes.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));

create policy vault_items_require_aal2 on public.vault_items
  as restrictive for all to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2')
  with check ((select auth.jwt() ->> 'aal') = 'aal2');
create policy vault_items_select_own on public.vault_items
  for select to authenticated using (exists (
    select 1 from public.vaults
    where vaults.id = vault_items.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));
create policy vault_items_insert_own on public.vault_items
  for insert to authenticated with check (exists (
    select 1 from public.vaults
    where vaults.id = vault_items.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));
create policy vault_items_update_own on public.vault_items
  for update to authenticated
  using (exists (
    select 1 from public.vaults
    where vaults.id = vault_items.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ))
  with check (exists (
    select 1 from public.vaults
    where vaults.id = vault_items.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));
create policy vault_items_delete_own on public.vault_items
  for delete to authenticated using (exists (
    select 1 from public.vaults
    where vaults.id = vault_items.vault_id
      and vaults.owner_user_id = (select auth.uid())
  ));

create policy security_events_require_aal2 on public.security_events
  as restrictive for select to authenticated
  using ((select auth.jwt() ->> 'aal') = 'aal2');
create policy security_events_select_own on public.security_events
  for select to authenticated using ((select auth.uid()) = user_id);

-- Reset Supabase's broad defaults, then grant only operations used by the product.
revoke all on all tables in schema public from public, anon, authenticated;
revoke all on all sequences in schema public from public, anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;

grant usage on schema public to anon, authenticated;

grant select on public.profiles to authenticated;
grant update (display_name, notifications_enabled) on public.profiles to authenticated;
grant select on public.user_preferences to authenticated;
grant update (theme, locale, auto_lock_seconds, clipboard_clear_seconds)
  on public.user_preferences to authenticated;
grant select, insert on public.vaults to authenticated;
grant update (encrypted_metadata, metadata_nonce, crypto_version)
  on public.vaults to authenticated;
grant select, insert on public.vault_key_envelopes to authenticated;
grant select, insert, delete on public.vault_items to authenticated;
grant update (
  encrypted_payload,
  nonce,
  cipher_algorithm,
  crypto_version,
  payload_schema_version,
  revision,
  deleted_at
) on public.vault_items to authenticated;
grant select on public.security_events to authenticated;

revoke all on function public.set_updated_at() from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.prevent_last_verified_totp_factor() from public, anon, authenticated;

alter default privileges in schema public
  revoke all on tables from public, anon, authenticated;
alter default privileges in schema public
  revoke all on sequences from public, anon, authenticated;
alter default privileges in schema public
  revoke all on functions from public, anon, authenticated;
