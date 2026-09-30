#!/usr/bin/env bash
# PostToolUse (Write|Edit): formatea con Spotless (AOSP) solo el .java editado, usando Maven en Docker.
f=$(python3 -c 'import sys,json; d=json.load(sys.stdin); print((d.get("tool_response") or {}).get("filePath") or d.get("tool_input",{}).get("file_path",""))')
[[ "$f" == *.java ]] || exit 0
root="${CLAUDE_PROJECT_DIR:-$(git -C "$(dirname "$f")" rev-parse --show-toplevel)}"
rel="${f#"$root"/}"
regex=".*/$(printf '%s' "$rel" | sed 's/[.[\*^$()+?{|]/\\&/g')"
docker run --rm -v "$root":/app -v m2-cache:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn -q -B -o spotless:apply -DspotlessFiles="$regex"
