// Build-time adapter. No network access or application runtime dependency.
const fs = require('node:fs');
const { FluentParser } = require('./fluent-syntax.cjs');
const source = fs.readFileSync(process.argv[2], 'utf8');
const resource = new FluentParser({ withSpans: true }).parse(source);
// Decode Fluent string escapes with the reference implementation, not JSON rules.
process.stdout.write(JSON.stringify(resource, (key, value) => {
  if (value && value.type === 'StringLiteral' && typeof value.parse === 'function') {
    return { ...value, value: value.parse().value };
  }
  return value;
}));
