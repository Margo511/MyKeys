import { readdir, readFile } from 'node:fs/promises';
import { extname, join, relative, resolve } from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const ignoredDirectories = new Set([
  '.expo',
  '.git',
  '.gradle',
  '.gradle-user-home',
  '.java-tmp',
  '.npm-cache',
  '.tooling',
  'build',
  'coverage',
  'dist',
  'node_modules',
]);
const textExtensions = new Set([
  '.gradle',
  '.java',
  '.js',
  '.json',
  '.kt',
  '.kts',
  '.md',
  '.mjs',
  '.plist',
  '.properties',
  '.sh',
  '.sql',
  '.swift',
  '.toml',
  '.ts',
  '.tsx',
  '.xcconfig',
  '.xml',
  '.yaml',
  '.yml',
]);
const forbiddenPatterns = [
  { label: 'Supabase secret key value', pattern: /sb_secret_[A-Za-z0-9_-]{20,}/ },
  { label: 'private key material', pattern: /BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY/ },
  {
    label: 'JWT-like token value',
    pattern: /eyJ[a-zA-Z0-9_-]{20,}\.[a-zA-Z0-9_-]{20,}\.[a-zA-Z0-9_-]{10,}/,
  },
];

async function listFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(
    entries
      .filter((entry) => !entry.isDirectory() || !ignoredDirectories.has(entry.name))
      .map((entry) => {
        const path = join(directory, entry.name);
        return entry.isDirectory() ? listFiles(path) : [path];
      }),
  );
  return nested.flat();
}

const files = (await listFiles(root)).filter((file) => textExtensions.has(extname(file)));
const findings = [];
for (const file of files) {
  const content = await readFile(file, 'utf8');
  for (const { label, pattern } of forbiddenPatterns) {
    if (pattern.test(content)) findings.push(`${label}: ${relative(root, file)}`);
  }
}

if (findings.length > 0) {
  process.stderr.write(`Source secret scan failed:\n${findings.join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`Source secret scan passed (${files.length} text files checked).\n`);
}
