// Read-only release hygiene checks. This is not a comprehensive secret/security scanner.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const ignored = new Set(['.git', 'target', 'node_modules', 'data', 'backups', 'dist', '.idea']);
function walk(folder) {
  return fs.readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    if (ignored.has(entry.name)) return [];
    const file = path.join(folder, entry.name);
    if (entry.isSymbolicLink()) throw new Error('Linked source file: ' + path.relative(root, file));
    return entry.isDirectory() ? walk(file) : [file];
  });
}
const files = walk(root);
const problems = [];
for (const file of files) {
  const relative = path.relative(root, file).replaceAll('\\', '/');
  // Local historical notes are not exported. Check public docs at their rendered export location.
  if (['README.md', 'UPGRADE.md', 'RESEARCH.md', 'HELP.md'].includes(relative) && fs.existsSync(path.join(root, 'docs/public'))) continue;
  const text = fs.readFileSync(file, 'utf8');
  if (/local-secrets\.xml$|\.(?:mv\.db|trace\.db|pem|p12|pfx|key|log|exe|jar|class)$|(?:^|\/)\.env(?:\.|$)/i.test(relative)) problems.push('Forbidden file: ' + relative);
  if (text.includes('\0')) problems.push('Unexpected binary: ' + relative);
  const secretPatterns = [/\bgh[pousr]_[A-Za-z0-9]{20,}\b/, /\bgithub_pat_[A-Za-z0-9_]{30,}\b/, /\bsk-[A-Za-z0-9_-]{24,}\b/, /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/, /\bAKIA[A-Z0-9]{16}\b/];
  if (secretPatterns.some(pattern => pattern.test(text))) problems.push('Review credential pattern (value redacted): ' + relative);
  if (/[CE]:[\\/]Users[\\/][^\s"']+/.test(text)) problems.push('Personal machine path: ' + relative);
  if (file.endsWith('.md') && !relative.startsWith('docs/public/')) {
    for (const match of text.matchAll(/\[[^\]]*\]\(([^\s)]+)\)/g)) {
      const link = match[1].replace(/^<|>$/g, '').split('#')[0];
      if (!link || /^[a-z]+:|^\/\//i.test(link)) continue;
      if (!fs.existsSync(path.resolve(path.dirname(file), decodeURIComponent(link)))) problems.push('Missing document link: ' + relative + ' -> ' + link);
    }
  }
}
const staticDir = path.join(root, 'src/main/resources/static');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
for (const match of html.matchAll(/(?:src|href)="([^"#]+)"/g)) {
  const link = match[1].split('?')[0];
  if (/^[a-z]+:|^\/\//i.test(link)) continue;
  if (!fs.existsSync(path.join(staticDir, link.replace(/^\//, '')))) problems.push('Missing UI resource: ' + link);
}
for (const [folder, count] of [['migration', 9], ['local', 7]]) {
  const entries = fs.readdirSync(path.join(root, 'src/main/resources/db', folder));
  for (let i = 1; i <= count; i++) {
    if (entries.filter(name => name.startsWith(`V${i}__`) && name.endsWith('.sql')).length !== 1) problems.push('Migration missing/duplicate: ' + folder + '/V' + i);
  }
}
if (problems.length) { console.error(problems.join('\n')); process.exitCode = 1; }
else console.log(`PASS source hygiene, relative documentation links, UI assets and 16 migrations (${files.length} files checked).`);
