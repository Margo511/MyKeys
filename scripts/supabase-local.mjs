#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const commands = {
  lint: ['db', 'lint', '--local', '--schema', 'public', '--level', 'warning'],
  publishableKey: ['status', '-o', 'env'],
  start: ['start'],
  status: ['status'],
  stop: ['stop'],
  test: ['test', 'db'],
};

const action = process.argv[2];
const command = commands[action];

if (!command && action !== 'reset') {
  process.stderr.write(
    'Uso: node scripts/supabase-local.mjs <start|stop|status|reset|test|lint|publishableKey>\n',
  );
  process.exit(2);
}

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function exitFrom(result, context) {
  if (result.error) {
    process.stderr.write(`${context}: ${result.error.message}\n`);
    process.exit(1);
  }

  process.exit(result.status ?? 1);
}

function printSafeStatus(result) {
  if (result.error || result.status !== 0) {
    if (result.error) {
      process.stderr.write(`No se pudo consultar Supabase local: ${result.error.message}\n`);
    } else {
      process.stderr.write(
        `Supabase local no esta disponible (codigo ${result.status ?? 'desconocido'}).\n`,
      );
    }
    process.exit(1);
  }

  process.stdout.write(
    [
      'Supabase local esta activo.',
      'API: http://127.0.0.1:54321',
      'Database: 127.0.0.1:54322 (local)',
      'Studio: http://127.0.0.1:54323',
      'Mailpit: http://127.0.0.1:54324',
      '',
    ].join('\n'),
  );
  process.exit(0);
}

function printPublishableKey(result) {
  if (result.error || result.status !== 0) {
    process.stderr.write('No se pudo leer la publishable key del stack local.\n');
    process.exit(1);
  }
  const match = result.stdout.match(/PUBLISHABLE_KEY="(sb_publishable_[A-Za-z0-9_-]+)"/);
  if (!match) {
    process.stderr.write('Supabase CLI no devolvio una publishable key.\n');
    process.exit(1);
  }
  process.stdout.write(`${match[1]}\n`);
  process.exit(0);
}

function sanitizeSupabaseOutput(value = '') {
  return value
    .replaceAll(/sb_(?:publishable|secret)_[A-Za-z0-9_-]+/g, '[credential redacted]')
    .replaceAll(/postgresql:\/\/[^\s@]+@/g, 'postgresql://[credential redacted]@')
    .split(/(?<=\n)/)
    .map((line) =>
      /Access Key|Secret Key/.test(line)
        ? line.replaceAll(/[a-f0-9]{32,64}/gi, '[credential redacted]')
        : line,
    )
    .join('');
}

function finishCaptured(result, context) {
  process.stdout.write(sanitizeSupabaseOutput(result.stdout));
  process.stderr.write(sanitizeSupabaseOutput(result.stderr));
  exitFrom(result, context);
}

function quoteForBash(value) {
  return `'${value.replaceAll("'", `'"'"'`)}'`;
}

if (process.platform === 'win32') {
  const distro = process.env.MY_KEYS_WSL_DISTRO ?? 'Ubuntu-24.04';
  const pathResult = spawnSync(
    'wsl.exe',
    ['-d', distro, '-u', 'root', '--', 'wslpath', '-a', projectRoot],
    { encoding: 'utf8' },
  );

  if (pathResult.error || pathResult.status !== 0) {
    process.stderr.write(pathResult.stderr ?? '');
    exitFrom(pathResult, `No se pudo abrir ${distro}`);
  }

  const wslProjectRoot = pathResult.stdout.trim();
  const ensureDocker =
    'systemctl start docker && ' +
    '(docker network inspect local-network >/dev/null 2>&1 || ' +
    'docker network create -o com.docker.network.bridge.host_binding_ipv4=127.0.0.1 local-network >/dev/null)';
  const supabaseCommand =
    action === 'reset'
      ? 'bash scripts/supabase-reset-local.sh'
      : action === 'start'
        ? 'supabase start --network-id local-network'
        : `supabase ${command.map(quoteForBash).join(' ')}`;
  const script = `cd ${quoteForBash(wslProjectRoot)} && ${ensureDocker} && ${supabaseCommand}`;
  const captureOutput =
    action === 'status' || action === 'start' || action === 'reset' || action === 'publishableKey';
  const result = spawnSync('wsl.exe', ['-d', distro, '-u', 'root', '--', 'bash', '-lc', script], {
    ...(captureOutput ? { encoding: 'utf8' } : { stdio: 'inherit' }),
  });

  if (action === 'status') printSafeStatus(result);
  if (action === 'publishableKey') printPublishableKey(result);
  if (action === 'start') {
    if (result.error || result.status !== 0)
      finishCaptured(result, 'No se pudo iniciar Supabase local');
    process.stdout.write(
      'Supabase local esta activo. Ejecuta npm run supabase:status para ver las URLs.\n',
    );
    process.exit(0);
  }
  if (action === 'reset') {
    if (result.error || result.status !== 0) {
      finishCaptured(result, 'No se pudo reiniciar Supabase local');
    }
    process.stdout.write('Supabase local reiniciado; migraciones y seed aplicados.\n');
    process.exit(0);
  }

  exitFrom(result, 'Supabase local fallo dentro de WSL');
}

const nativeCommand = action === 'reset' ? ['db', 'reset', '--local'] : command;
const captureNativeOutput =
  action === 'status' || action === 'start' || action === 'reset' || action === 'publishableKey';
const result = spawnSync('supabase', nativeCommand, {
  cwd: projectRoot,
  ...(captureNativeOutput ? { encoding: 'utf8' } : { stdio: 'inherit' }),
});

if (action === 'status') printSafeStatus(result);
if (action === 'publishableKey') printPublishableKey(result);
if (action === 'start') {
  if (result.error || result.status !== 0)
    finishCaptured(result, 'No se pudo iniciar Supabase local');
  process.stdout.write(
    'Supabase local esta activo. Ejecuta npm run supabase:status para ver las URLs.\n',
  );
  process.exit(0);
}
if (action === 'reset') {
  if (result.error || result.status !== 0) {
    finishCaptured(result, 'No se pudo reiniciar Supabase local');
  }
  process.stdout.write('Supabase local reiniciado; migraciones y seed aplicados.\n');
  process.exit(0);
}

exitFrom(result, 'Supabase CLI no esta disponible');
