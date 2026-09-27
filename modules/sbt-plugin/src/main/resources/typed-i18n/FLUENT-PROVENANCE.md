# Vendored Fluent parser

`fluent-syntax.cjs` is the unmodified `index.js` from the official
`@fluent/syntax` npm package, version **0.19.0** (Apache-2.0).

- Upstream: https://github.com/projectfluent/fluent.js
- Archive: https://registry.npmjs.org/@fluent/syntax/-/syntax-0.19.0.tgz
- Archive SHA-1 (npm shasum): `43f882faba6908b0f1013f6a94e009d0dfbdcb77`
- License and attribution: `FLUENT-LICENSE`, `FLUENT-NOTICE`.

The sbt plugin invokes this parser using Node at build time. No npm install,
network download, JavaScript parser, or Fluent interpreter is needed by the
compiled application. Our adapter and compiler deliberately support only the
documented subset of the syntax tree; unsupported constructs fail the build.
