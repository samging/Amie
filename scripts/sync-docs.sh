#!/bin/zsh
# Copies the desktop app's documentation (the MkDocs pages in amieAppStable/docs) into the website as Kobweb
# markdown pages under app/site/src/jsMain/resources/markdown/docs, served at /docs/<page>.
#
#   scripts/sync-docs.sh                  # from ../amieAppStable/docs (or $AMIE_APP_DOCS)
#   scripts/sync-docs.sh /path/to/docs    # from another checkout
#
# For every <name>.md it writes <PascalName>.md with the front matter Kobweb needs (root layout, routeOverride,
# title taken from the first heading), rewrites MkDocs links (other.md, other.md#anchor) to site routes
# (/docs/other, /docs/other#anchor), escapes bare <placeholders> outside code so the markdown plugin does not read
# them as HTML tags, and replaces the "built with MkDocs" paragraph of index.md with one that fits the site.
# Run it after pulling amieAppStable, then rebuild the site (./gradlew :site:kobwebStart picks the pages up).
set -euo pipefail

here=${0:A:h}
repo=${here:h}
src=${1:-${AMIE_APP_DOCS:-$repo/../amieAppStable/docs}}
dst=$repo/app/site/src/jsMain/resources/markdown/docs

if [[ ! -d $src ]]; then
  echo "sync-docs: no docs folder at $src (pass the path or set AMIE_APP_DOCS)" >&2
  exit 1
fi

mkdir -p "$dst"
count=0
for file in "$src"/*.md; do
  name=$(basename "$file" .md)
  pascal=$(echo "$name" | perl -pe 's/(^|-)([a-z0-9])/\U$2/g')
  if [[ $name == index ]]; then route="/docs/"; else route="/docs/$name"; fi
  title=$(grep -m1 '^# ' "$file" | sed 's/^# //')
  out=$dst/$pascal.md
  {
    printf -- '---\nroot: .components.layouts.MarkdownLayout\nrouteOverride: %s\ntitle: %s\n---\n\n' "$route" "$title"
    perl -0777 -pe '
      # the MkDocs paragraph of the overview page becomes a pointer to the app repository
      s/This site is plain Markdown in the repository.s `docs\/` folder.*?```bash\n.*?```\n/These pages describe the Amie desktop app (`amieAppStable`). The same text lives as Markdown in that repository'"'"'s `docs\/` folder.\n/s;
    ' "$file" | perl -pe '
      BEGIN { $fence = 0 }
      if (/^\s*(```|~~~)/) { $fence = !$fence; next }
      next if $fence;
      # MkDocs page links -> site routes
      s/\]\(([a-z0-9-]+)\.md(#[^)]*)?\)/"](\/docs\/$1" . ($2 \/\/ "") . ")"/ge;
      # bare <placeholder> outside inline code -> \<placeholder\>
      s/(`[^`]*`)|<([A-Za-z][A-Za-z0-9_-]*)>/defined $1 ? $1 : "\\<$2\\>"/ge;
      # Kobweb 0.19.1 copies inline code into a Kotlin string without escaping backslashes (a Windows path such as
      # `%APPDATA%\amie` fails compileKotlinJs with "Unsupported escape sequence"), so double them inside code spans
      s/`([^`]*)`/"`" . ($1 =~ s{\\}{\\\\}gr) . "`"/ge;
    '
  } > "$out"
  count=$((count + 1))
done

# pages that no longer exist in the app docs are removed from the site too
for file in "$dst"/*.md; do
  pascal=$(basename "$file" .md)
  kebab=$(echo "$pascal" | perl -pe 's/([a-z0-9])([A-Z])/$1-$2/g; $_ = lc')
  [[ -f $src/$kebab.md ]] || { echo "sync-docs: removing $file (no $kebab.md in $src)"; rm "$file"; }
done

echo "sync-docs: wrote $count pages from $src to $dst"

# the site search lists every heading of these pages; rebuild that index too
"$here/docs-index.sh"
