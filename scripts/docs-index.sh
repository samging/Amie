#!/bin/zsh
# Rebuilds the documentation search index (DocsIndex.kt) from the markdown pages under
# app/site/src/jsMain/resources/markdown/docs. scripts/sync-docs.sh runs it; run it by hand after editing a page.
#
#   scripts/docs-index.sh
set -euo pipefail

here=${0:A:h}
repo=${here:h}
python3 "$here/docs-index.py" "$repo/app/site/src/jsMain/resources/markdown/docs" \
  "$repo/app/site/src/jsMain/kotlin/org/example/app/components/DocsIndex.kt"
