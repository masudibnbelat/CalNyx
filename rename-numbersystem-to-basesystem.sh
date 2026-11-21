#!/usr/bin/env bash
# CalNyx - rename Number System (file/class) to Base System
# Run from project root (CalNyx folder):
#   bash rename-numbersystem-to-basesystem.sh
#   ktdev build
#
# What it renames:
#   file:  NumberSystemCalculatorScreen.kt -> BaseSystemCalculatorScreen.kt
#          (in calculator/ after reorganize, or in ui/screens/ if
#           reorganize was not run - the script finds it either way;
#           any other *NumberSystem*.kt file is renamed the same way)
#   class: NumberSystemCalculatorScreen -> BaseSystemCalculatorScreen
#          everywhere it is used (NavGraph, imports, etc.)
#   text:  "Number System" -> "Base System" in .kt and res xml
#          (drawer/header were already Base System; this catches
#           any leftover title string)
#
# What it does NOT change (on purpose):
#   - route string "numbersystem" in NavGraph (changing it would break
#     saved navigation state; it is internal only)
set -e
BASE="app/src/main/java/com/calnyx/app"

if [ ! -d "$BASE" ]; then
  echo "ERROR: $BASE not found. Run this from the CalNyx project root."
  exit 1
fi

# --- 1) file(s) *NumberSystem*.kt -> *BaseSystem*.kt ---
found=0
while IFS= read -r src; do
  [ -z "$src" ] && continue
  found=1
  dir=$(dirname "$src")
  name=$(basename "$src")
  newname=${name//NumberSystem/BaseSystem}
  if [ "$name" != "$newname" ]; then
    git mv "$src" "$dir/$newname"
    echo "moved $src -> $dir/$newname"
  fi
done <<EOF
$(find "$BASE" -name "*NumberSystem*.kt" 2>/dev/null)
EOF

if [ "$found" -eq 0 ]; then
  if find "$BASE" -name "*BaseSystem*.kt" 2>/dev/null | grep -q .; then
    echo "Already renamed: a *BaseSystem*.kt file exists, nothing to do."
    exit 0
  fi
  echo "ERROR: no *NumberSystem*.kt file found under $BASE."
  exit 1
fi

echo ""
echo "=== Step 2: renaming NumberSystem -> BaseSystem, 'Number System' -> 'Base System' ==="
python3 - "$BASE" <<'PYEOF'
import os, sys
base = sys.argv[1]
changed = []
# .kt files under BASE
targets = []
for root, _, files in os.walk(base):
    for fn in files:
        if fn.endswith(".kt"):
            targets.append(os.path.join(root, fn))
# res xml too (strings.xml may still say "Number System")
for root, _, files in os.walk("app/src/main/res"):
    for fn in files:
        if fn.endswith(".xml"):
            targets.append(os.path.join(root, fn))
for p in targets:
    old = open(p, encoding="utf-8").read()
    # "NumberSystem" first (covers NumberSystemCalculatorScreen),
    # then the spaced display text. "numbersystem" (lowercase route)
    # has no "NumberSystem" substring, so it is untouched.
    new = old.replace("NumberSystem", "BaseSystem")
    new = new.replace("Number System", "Base System")
    if new != old:
        open(p, "w", encoding="utf-8").write(new); changed.append(p)
print(f"fixed {len(changed)} files")
for p in changed: print("  ", p)
PYEOF

echo ""
echo "=== verify ==="
echo "-- new file --"
find "$BASE" -name "*BaseSystem*.kt" -o -name "*NumberSystem*.kt" | sort
echo ""
echo "-- leftover NumberSystem in code (should be empty) --"
grep -rn "NumberSystem" --include="*.kt" "$BASE" 2>/dev/null || echo "OK: no NumberSystem left in code"
grep -rn "Number System" --include="*.kt" "$BASE" app/src/main/res 2>/dev/null || echo "OK: no 'Number System' text left"
echo ""
echo "-- kept as-is (do not worry about this) --"
grep -rn "numbersystem" --include="*.kt" "$BASE" 2>/dev/null || echo "(no 'numbersystem' route found)"
echo ""
echo "Now run: ktdev build"
echo "If the build complains about a missing import, run: bash fix-imports.sh"
