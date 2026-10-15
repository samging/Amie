#!/usr/bin/env bash
# Builds a package jar the Amie store accepts: one public class with `run(String): List` and `close()`.
#
#   scripts/examples/make-package-jar.sh                      # writes echo-package.jar in the current folder
#   scripts/examples/make-package-jar.sh my-tool.jar MyTool   # a jar and class name of your own
#   scripts/examples/make-package-jar.sh --broken bad.jar     # a jar WITHOUT close(), to see the store refuse it
#
# Needs a JDK (javac + jar). Uses $JAVA_HOME when set, otherwise the JDK 21 run.sh uses.
set -euo pipefail

broken=0
if [[ "${1:-}" == "--broken" ]]; then broken=1; shift; fi
out="${1:-echo-package.jar}"
cls="${2:-EchoPackage}"

JAVA_HOME="${JAVA_HOME:-/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home}"
javac="$JAVA_HOME/bin/javac"; jar="$JAVA_HOME/bin/jar"
for tool in "$javac" "$jar"; do
  [[ -x "$tool" ]] || { echo "error: $tool not found; set JAVA_HOME to a JDK" >&2; exit 2; }
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if (( broken )); then
  # Looks like a package but has no close(): the store answers 400 "has no close()"
  cat > "$work/$cls.java" <<EOF
import java.util.List;
public class $cls {
    public List<String> run(String input) { return List.of("echo: " + input); }
}
EOF
else
  cat > "$work/$cls.java" <<EOF
import java.util.ArrayList;
import java.util.List;

/** The smallest Amie package: run() turns the text it gets into a list of lines, close() frees nothing. */
public class $cls {
    public List<String> run(String input) {
        List<String> out = new ArrayList<>();
        for (String line : input.split("\\\\R")) out.add("echo: " + line);
        return out;
    }
    public void close() {}
}
EOF
fi

"$javac" -d "$work" "$work/$cls.java"
"$jar" --create --file "$out" -C "$work" "$cls.class"
echo "wrote $out ($(wc -c < "$out" | tr -d ' ') bytes, class $cls$( (( broken )) && echo ', no close(): the store will refuse it'))"
