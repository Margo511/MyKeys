import { readdir, readFile } from 'node:fs/promises';
import { extname, join, resolve } from 'node:path';

const textExtensions = new Set(['.html', '.js', '.json', '.map', '.txt']);
const forbiddenPatterns = [
  { label: 'Supabase server secret identifier', pattern: /SUPABASE_(?:SECRET|SERVICE_ROLE)_KEY/i },
  { label: 'email provider secret identifier', pattern: /EMAIL_PROVIDER_API_KEY/i },
  { label: 'private key material', pattern: /BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY/i },
  {
    label: 'JWT-like token',
    pattern: /eyJ[a-zA-Z0-9_-]{20,}\.[a-zA-Z0-9_-]{20,}\.[a-zA-Z0-9_-]{10,}/,
  },
];

async function listFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(
    entries.map((entry) => {
      const path = join(directory, entry.name);
      return entry.isDirectory() ? listFiles(path) : [path];
    }),
  );

  return files.flat();
}

const target = resolve(process.argv[2] ?? 'dist');
const files = (await listFiles(target)).filter((file) => textExtensions.has(extname(file)));
const findings = [];

for (const file of files) {
  const content = await readFile(file, 'utf8');
  for (const { label, pattern } of forbiddenPatterns) {
    if (pattern.test(content)) {
      findings.push(`${label}: ${file}`);
    }
  }
}

if (findings.length > 0) {
  process.stderr.write(`Bundle secret scan failed:\n${findings.join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`Bundle secret scan passed (${files.length} text files checked).\n`);
}
