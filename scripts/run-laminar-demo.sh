#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_directory}/.." && pwd)"
demo_directory="${repository_root}/modules/sbt-plugin/src/sbt-test/typed-i18n/laminar-app"
demo_version="${TYPED_I18N_DEMO_VERSION:-0.1.0-SNAPSHOT}"
demo_port="${1:-8080}"

cd "${repository_root}"
sbt "runtimeJS/publishLocal" "plugin/publishLocal"

cd "${demo_directory}"
# Refresh the meta-build too: a locally rebuilt plugin may add dependencies
# without changing its SNAPSHOT version or the fixture's plugins.sbt.
sbt -Dplugin.version="${demo_version}" \
  "reload plugins" update "reload return" update fastLinkJS

printf 'Laminar demo: http://127.0.0.1:%s/index.html\n' "${demo_port}"
printf 'Press Ctrl-C to stop.\n'
exec python3 -m http.server "${demo_port}" --bind 127.0.0.1
