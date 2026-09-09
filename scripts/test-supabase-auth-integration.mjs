#!/usr/bin/env node

import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { createHmac, randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createClient } from '@supabase/supabase-js';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const apiUrl = 'http://127.0.0.1:54321';
const mailpitUrl = 'http://127.0.0.1:54324';

function quoteForBash(value) {
  return `'${value.replaceAll("'", `'"'"'`)}'`;
}

function readLocalEnvironment() {
  let result;
  if (process.platform === 'win32') {
    const distro = process.env.MY_KEYS_WSL_DISTRO ?? 'Ubuntu-24.04';
    const pathResult = spawnSync(
      'wsl.exe',
      ['-d', distro, '-u', 'root', '--', 'wslpath', '-a', projectRoot],
      { encoding: 'utf8' },
    );
    assert.equal(pathResult.status, 0, 'No se pudo resolver el workspace dentro de WSL.');
    const command = `cd ${quoteForBash(pathResult.stdout.trim())} && supabase status -o env`;
    result = spawnSync('wsl.exe', ['-d', distro, '-u', 'root', '--', 'bash', '-lc', command], {
      encoding: 'utf8',
    });
  } else {
    result = spawnSync('supabase', ['status', '-o', 'env'], {
      cwd: projectRoot,
      encoding: 'utf8',
    });
  }

  assert.equal(result.status, 0, 'Supabase local debe estar iniciado.');
  return Object.fromEntries(
    result.stdout
      .split(/\r?\n/)
      .filter((line) => line.includes('='))
      .map((line) => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator), line.slice(separator + 1).replace(/^"|"$/g, '')];
      }),
  );
}

function client(publishableKey) {
  return createClient(apiUrl, publishableKey, {
    auth: {
      autoRefreshToken: false,
      detectSessionInUrl: false,
      persistSession: false,
    },
  });
}

function safeError(error) {
  if (error?.name === 'AssertionError') return error.message;
  return error?.code ?? error?.message ?? error?.name ?? 'error desconocido';
}

function requireSuccess(error, context) {
  if (error) throw new Error(`${context}: ${safeError(error)}`);
}

async function waitForAuthUrl(email, expectedType) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const response = await fetch(
      `${mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
    );
    if (response.ok) {
      const search = await response.json();
      for (const summary of search.messages ?? []) {
        const id = summary?.ID ?? summary?.Id ?? summary?.id;
        if (!id) continue;
        const messageResponse = await fetch(`${mailpitUrl}/api/v1/message/${id}`);
        assert.equal(
          messageResponse.ok,
          true,
          'Mailpit no pudo devolver el email de verificacion.',
        );
        const message = await messageResponse.json();
        const source = [message.HTML, message.Text, message.html, message.text]
          .filter(Boolean)
          .join('\n')
          .replaceAll('&amp;', '&');
        const urls = source.match(/https?:\/\/[^\s"'<>]+/g) ?? [];
        const confirmationUrl = urls.find((value) => {
          if (!value.includes('/auth/v1/verify')) return false;
          return new URL(value).searchParams.get('type') === expectedType;
        });
        if (confirmationUrl) return confirmationUrl;
      }
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }
  throw new Error(`No llego el email local de tipo ${expectedType} a Mailpit.`);
}

async function adoptAuthLink(authClient, authUrl, expectedType) {
  const response = await fetch(authUrl, { redirect: 'manual' });
  assert.ok(
    response.status === 302 || response.status === 303,
    'El enlace de verificacion no produjo la redireccion esperada.',
  );
  const location = response.headers.get('location');
  assert.ok(
    location?.startsWith('mykeys://auth/callback'),
    'El callback no usa el esquema de My Keys.',
  );

  const callback = new URL(location);
  const parameters = new URLSearchParams(callback.hash.slice(1));
  assert.equal(
    parameters.get('type'),
    expectedType,
    'El callback tiene un tipo de Auth inesperado.',
  );
  const accessToken = parameters.get('access_token');
  const refreshToken = parameters.get('refresh_token');
  assert.ok(accessToken && refreshToken, 'El callback de verificacion no contiene una sesion.');

  const { error } = await authClient.auth.setSession({
    access_token: accessToken,
    refresh_token: refreshToken,
  });
  requireSuccess(error, 'No se pudo adoptar la sesion verificada');
}

async function confirmEmail(authClient, email) {
  await adoptAuthLink(authClient, await waitForAuthUrl(email, 'signup'), 'signup');
}

function decodeBase32(value) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const character of value.toUpperCase().replaceAll('=', '').replaceAll(' ', '')) {
    const index = alphabet.indexOf(character);
    assert.notEqual(index, -1, 'El secreto TOTP recibido no es base32 valido.');
    bits += index.toString(2).padStart(5, '0');
  }
  const bytes = [];
  for (let offset = 0; offset + 8 <= bits.length; offset += 8) {
    bytes.push(Number.parseInt(bits.slice(offset, offset + 8), 2));
  }
  return Buffer.from(bytes);
}

function totp(secret, timeStepOffset = 0) {
  const counter = Math.floor(Date.now() / 30_000) + timeStepOffset;
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  const digest = createHmac('sha1', decodeBase32(secret)).update(message).digest();
  const offset = digest.at(-1) & 0x0f;
  const binary =
    ((digest[offset] & 0x7f) << 24) |
    ((digest[offset + 1] & 0xff) << 16) |
    ((digest[offset + 2] & 0xff) << 8) |
    (digest[offset + 3] & 0xff);
  return String(binary % 1_000_000).padStart(6, '0');
}

async function registerAndConfirm(authClient, email, password) {
  const { data, error } = await authClient.auth.signUp({
    email,
    password,
    options: { emailRedirectTo: 'mykeys://auth/callback' },
  });
  requireSuccess(error, 'Registro fallido');
  assert.equal(
    data.session,
    null,
    'El registro no debe iniciar sesion antes de verificar el email.',
  );
  await confirmEmail(authClient, email);
}

async function enrollAndVerify(authClient, friendlyName) {
  const { data: enrollment, error: enrollError } = await authClient.auth.mfa.enroll({
    factorType: 'totp',
    friendlyName,
  });
  requireSuccess(enrollError, 'Alta TOTP fallida');
  assert.ok(
    enrollment.id && enrollment.totp?.secret && enrollment.totp?.uri,
    'Alta TOTP incompleta.',
  );

  let verificationError;
  for (const offset of [0, -1, 1]) {
    const result = await authClient.auth.mfa.challengeAndVerify({
      factorId: enrollment.id,
      code: totp(enrollment.totp.secret, offset),
    });
    verificationError = result.error;
    if (!verificationError) break;
  }
  requireSuccess(verificationError, 'Verificacion TOTP fallida');
  return { id: enrollment.id, secret: enrollment.totp.secret };
}

async function verifyExistingFactor(authClient, factor) {
  let verificationError;
  for (const offset of [0, -1, 1]) {
    const result = await authClient.auth.mfa.challengeAndVerify({
      factorId: factor.id,
      code: totp(factor.secret, offset),
    });
    verificationError = result.error;
    if (!verificationError) break;
  }
  requireSuccess(verificationError, 'El desafio de un factor TOTP existente fallo');
}

async function main() {
  const localEnvironment = readLocalEnvironment();
  const publishableKey = localEnvironment.PUBLISHABLE_KEY ?? localEnvironment.ANON_KEY;
  assert.ok(
    publishableKey?.startsWith('sb_publishable_'),
    'No se encontro la publishable key local.',
  );

  const anonymous = client(publishableKey);
  const suffix = `${Date.now()}-${randomUUID().slice(0, 8)}`;
  const password = `Local-only-${randomUUID()}!`;
  const emailA = `phase2-a-${suffix}@mykeys.test`;
  const emailB = `phase2-b-${suffix}@mykeys.test`;
  const userA = client(publishableKey);
  const userB = client(publishableKey);

  const anonymousProfiles = await anonymous.from('profiles').select('id');
  assert.ok(anonymousProfiles.error, 'Anon no debe tener permiso SELECT sobre perfiles.');

  await registerAndConfirm(userA, emailA, password);
  const sessionA = await userA.auth.getSession();
  const userAId = sessionA.data.session?.user.id;
  assert.ok(userAId, 'Usuario A no tiene una sesion AAL1 verificada.');

  const aal1Profiles = await userA.from('profiles').select('id');
  requireSuccess(aal1Profiles.error, 'Consulta AAL1 inesperadamente invalida');
  assert.deepEqual(aal1Profiles.data, [], 'AAL1 no debe ver datos privados propios.');

  const primaryFactorA = await enrollAndVerify(userA, 'Principal');
  const aalA = await userA.auth.mfa.getAuthenticatorAssuranceLevel();
  requireSuccess(aalA.error, 'No se pudo comprobar AAL de usuario A');
  assert.equal(aalA.data.currentLevel, 'aal2', 'Usuario A debe alcanzar AAL2.');

  const ownProfileA = await userA.from('profiles').select('id,email:display_name');
  requireSuccess(ownProfileA.error, 'Usuario A AAL2 no pudo leer su perfil');
  assert.equal(ownProfileA.data.length, 1, 'Usuario A AAL2 debe ver un unico perfil propio.');

  const lastFactorRemoval = await userA.auth.mfa.unenroll({ factorId: primaryFactorA.id });
  assert.ok(
    lastFactorRemoval.error,
    'El backend no debe permitir eliminar el ultimo TOTP verificado.',
  );

  const backupFactorA = await enrollAndVerify(userA, 'Respaldo');
  const primaryRemoval = await userA.auth.mfa.unenroll({ factorId: primaryFactorA.id });
  requireSuccess(primaryRemoval.error, 'Debe poder eliminarse un factor si queda otro verificado');
  assert.ok(backupFactorA.id, 'El factor de respaldo debe permanecer activo.');

  await registerAndConfirm(userB, emailB, password);
  const sessionB = await userB.auth.getSession();
  const userBId = sessionB.data.session?.user.id;
  assert.ok(userBId, 'Usuario B no tiene una sesion AAL1 verificada.');
  const factorB = await enrollAndVerify(userB, 'Principal');

  const vaultA = randomUUID();
  const vaultB = randomUUID();
  requireSuccess(
    (await userA.from('vaults').insert({ id: vaultA, owner_user_id: userAId })).error,
    'Usuario A no pudo crear su vault',
  );
  requireSuccess(
    (await userB.from('vaults').insert({ id: vaultB, owner_user_id: userBId })).error,
    'Usuario B no pudo crear su vault',
  );

  const guessedVault = await userA.from('vaults').select('id').eq('id', vaultB);
  requireSuccess(guessedVault.error, 'Consulta de UUID ajeno fallo de forma inesperada');
  assert.deepEqual(guessedVault.data, [], 'Usuario A no debe descubrir el vault de B por UUID.');

  const forgedOwner = await userA
    .from('vaults')
    .insert({ id: randomUUID(), owner_user_id: userBId });
  assert.ok(forgedOwner.error, 'Usuario A no debe forjar owner_user_id de B.');

  const movedOwner = await userA.from('vaults').update({ owner_user_id: userBId }).eq('id', vaultA);
  assert.ok(movedOwner.error, 'Los grants deben impedir cambiar la propiedad del vault.');

  const forgedMembership = await userA.from('vault_items').insert({
    id: randomUUID(),
    vault_id: vaultB,
    encrypted_payload: '\\x0000000000000000000000000000000000',
    nonce: '\\x000000000000000000000000',
  });
  assert.ok(forgedMembership.error, 'Usuario A no debe insertar elementos en el vault de B.');

  const malformedUuid = await userA.from('vaults').select('id').eq('id', 'not-a-uuid');
  assert.ok(malformedUuid.error, 'Un UUID manipulado debe rechazarse.');

  const profileBFromA = await userA.from('profiles').select('id').eq('id', userBId);
  requireSuccess(profileBFromA.error, 'Consulta aislada de perfil ajeno fallo');
  assert.deepEqual(profileBFromA.data, [], 'Usuario A no debe leer el perfil de B.');

  const passwordAfterReset = `Reset-local-${randomUUID()}!`;
  requireSuccess((await userB.auth.signOut({ scope: 'local' })).error, 'Logout local de B fallo');
  requireSuccess(
    (await userB.auth.resetPasswordForEmail(emailB, { redirectTo: 'mykeys://auth/callback' }))
      .error,
    'Solicitud de recuperacion fallo',
  );
  await adoptAuthLink(userB, await waitForAuthUrl(emailB, 'recovery'), 'recovery');
  const recoveryAal1 = await userB.from('profiles').select('id');
  requireSuccess(recoveryAal1.error, 'Consulta de recuperacion AAL1 fallo');
  assert.deepEqual(recoveryAal1.data, [], 'El enlace de recuperacion no debe omitir MFA.');
  await verifyExistingFactor(userB, factorB);
  requireSuccess(
    (await userB.auth.updateUser({ password: passwordAfterReset })).error,
    'No se pudo fijar la nueva contraseña de acceso',
  );
  requireSuccess(
    (await userB.auth.signOut({ scope: 'local' })).error,
    'Logout posterior al reset fallo',
  );
  requireSuccess(
    (await userB.auth.signInWithPassword({ email: emailB, password: passwordAfterReset })).error,
    'Login con la contraseña restablecida fallo',
  );
  const postResetAal1 = await userB.from('profiles').select('id');
  requireSuccess(postResetAal1.error, 'Consulta AAL1 posterior al reset fallo');
  assert.deepEqual(postResetAal1.data, [], 'Restablecer la contraseña no debe omitir MFA.');
  await verifyExistingFactor(userB, factorB);
  const postResetAal2 = await userB.from('profiles').select('id');
  requireSuccess(postResetAal2.error, 'Consulta AAL2 posterior al reset fallo');
  assert.equal(postResetAal2.data.length, 1, 'El login recuperado debe acceder solo tras AAL2.');

  process.stdout.write('Integracion local registro/email/TOTP/AAL2/RLS: OK\n');
}

main().catch((error) => {
  process.stderr.write(`Integracion local fallida: ${safeError(error)}\n`);
  process.exit(1);
});
