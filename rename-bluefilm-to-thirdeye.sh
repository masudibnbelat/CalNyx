#!/usr/bin/env bash
# CalNyx - rename BlueFilm (code) to Third Eye
# Run from project root (CalNyx folder):
#   bash rename-bluefilm-to-thirdeye.sh
#   ktdev build
#
# What it renames (code only):
#   folder:  app/.../app/bluefilm/  ->  app/.../app/thirdeye/
#            (no space - Kotlin package names cannot contain a space;
#             the display name on screen is already "Third Eye")
#   files:   BlueFilmCrypto.kt  -> ThirdEyeCrypto.kt
#            BlueFilmService.kt -> ThirdEyeService.kt
#            BlueFilmStore.kt   -> ThirdEyeStore.kt
#            BlueFilmScreen.kt  -> ThirdEyeScreen.kt
#            ScreenDrawView.kt, ScreenRecordService.kt,
#            ScreenRecordToolbarActivity.kt keep their names,
#            they just move to thirdeye/ with a new package.
#   classes: BlueFilmCrypto/Service/Store/Screen -> ThirdEye*
#            everywhere they are used (NavGraph, etc.)
#   package: com.calnyx.app.bluefilm -> com.calnyx.app.thirdeye
#   Manifest: .bluefilm.ScreenRecordService -> .thirdeye.ScreenRecordService
#
# What it does NOT change (on purpose):
#   - route string "blue_film" in NavGraph (changing it would break
#     saved navigation state)
#   - phone storage folder "My Blue Film" (renaming it would orphan
#     existing videos; say so if you want that migrated too)
set -e
BASE="app/src/main/java/com/calnyx/app"
MANIFEST="app/src/main/AndroidManifest.xml"

if [ ! -d "$BASE" ]; then
  echo "ERROR: $BASE not found. Run this from the CalNyx project root."
  exit 1
fi

# git mv if source exists, otherwise skip (safe to re-run)
mvf() {
  if [ -e "$1" ]; then git mv "$1" "$2"; echo "moved $1 -> $2";
  else echo "skip (not found): $1"; fi
}

# Old capital-B folder may still exist if reorganize was not run
if [ -d "$BASE/BlueFilm" ] && [ ! -d "$BASE/bluefilm" ]; then
  git mv "$BASE/BlueFilm" "$BASE/bluefilm"
  echo "moved $BASE/BlueFilm -> $BASE/bluefilm"
fi

if [ ! -d "$BASE/bluefilm" ]; then
  if [ -d "$BASE/thirdeye" ]; then
    echo "Already renamed: $BASE/thirdeye exists, nothing to do."
    exit 0
  fi
  echo "ERROR: $BASE/bluefilm not found - nothing to rename."
  exit 1
fi

# --- 1) folder bluefilm -> thirdeye ---
git mv "$BASE/bluefilm" "$BASE/thirdeye"
echo "moved $BASE/bluefilm -> $BASE/thirdeye"

# --- 2) files BlueFilm*.kt -> ThirdEye*.kt ---
mvf "$BASE/thirdeye/BlueFilmCrypto.kt"  "$BASE/thirdeye/ThirdEyeCrypto.kt"
mvf "$BASE/thirdeye/BlueFilmService.kt" "$BASE/thirdeye/ThirdEyeService.kt"
mvf "$BASE/thirdeye/BlueFilmStore.kt"   "$BASE/thirdeye/ThirdEyeStore.kt"
mvf "$BASE/thirdeye/BlueFilmScreen.kt"  "$BASE/thirdeye/ThirdEyeScreen.kt"

echo ""
echo "=== Step 2: renaming BlueFilm -> ThirdEye, bluefilm -> thirdeye ==="
python3 - "$BASE" "$MANIFEST" <<'PYEOF'
import os, re, sys
base, manifest = sys.argv[1], sys.argv[2]

def fix_kt(path):
    text = open(path, encoding="utf-8").read()
    old = text
    # package line = folder's package
    rel = os.path.relpath(os.path.dirname(path), base)
    pkg = "com.calnyx.app" if rel == "." else "com.calnyx.app." + rel.replace(os.sep, ".")
    text, n = re.subn(r"^package [A-Za-z0-9_.]+", f"package {pkg}", text, count=1, flags=re.M)
    # class / symbol rename: BlueFilmCrypto -> ThirdEyeCrypto, etc.
    # (plain prefix replace - "blue_film" and "Blue Film" have no
    #  "BlueFilm" substring, so they are untouched)
    text = text.replace("BlueFilm", "ThirdEye")
    text = text.replace("com.calnyx.app.bluefilm", "com.calnyx.app.thirdeye")
    if text != old:
        open(path, "w", encoding="utf-8").write(text)
        return True
    return False

changed = []
for root, _, files in os.walk(base):
    for fn in files:
        if fn.endswith(".kt"):
            p = os.path.join(root, fn)
            if fix_kt(p):
                changed.append(p)
# Manifest + res xml: short form .bluefilm.X and full BlueFilm names
for p in [manifest] + [os.path.join(r, f) for r, _, fs in os.walk("app/src/main/res") for f in fs if f.endswith(".xml")]:
    if os.path.exists(p):
        old = open(p, encoding="utf-8").read()
        new = old.replace(".bluefilm.", ".thirdeye.")
        new = new.replace("BlueFilm", "ThirdEye")
        new = new.replace("com.calnyx.app.bluefilm", "com.calnyx.app.thirdeye")
        if new != old:
            open(p, "w", encoding="utf-8").write(new); changed.append(p)
print(f"fixed {len(changed)} files")
for p in changed: print("  ", p)
PYEOF

echo ""
echo "=== verify ==="
echo "-- new folder --"
ls "$BASE/thirdeye/"
echo ""
echo "-- leftover BlueFilm / bluefilm in code (blue_film route + 'My Blue Film' storage folder are OK, they are listed separately below if present) --"
grep -rn "BlueFilm\|com\.calnyx\.app\.bluefilm\|\.bluefilm\." --include="*.kt" "$BASE" "$MANIFEST" 2>/dev/null || echo "OK: no BlueFilm/bluefilm left in code"
echo ""
echo "-- kept as-is (do not worry about these) --"
grep -rn "blue_film\|My Blue Film" --include="*.kt" "$BASE" 2>/dev/null || echo "(none found)"
echo ""
echo "Now run: ktdev build"
echo "If the build complains about a missing import, run: bash fix-imports.sh"
