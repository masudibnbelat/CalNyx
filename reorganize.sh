#!/usr/bin/env bash
# CalNyx - topic-wise folder reorganize
# Run from project root (CalNyx folder, where app/ exists):
#   bash reorganize.sh
# BEFORE running: commit everything - git add -A && git commit -m "before reorganize"
# After running: check `git status`, then build with `ktdev build`
set -e
BASE="app/src/main/java/com/calnyx/app"
MANIFEST="app/src/main/AndroidManifest.xml"

if [ ! -d "$BASE" ]; then
  echo "ERROR: $BASE not found. Run this from the CalNyx project root."
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 needed for package/import fixing."
  exit 1
fi

# git mv if source exists, otherwise skip (so re-running is safe)
mvf() {
  if [ -e "$1" ]; then git mv "$1" "$2"; echo "moved $1 -> $2";
  else echo "skip (not found): $1"; fi
}

mkdir -p "$BASE/core" "$BASE/theme" "$BASE/calculator" "$BASE/ball" \
  "$BASE/hardcore" "$BASE/moan" "$BASE/network" "$BASE/bluefilm"

# --- core: app entry point ---
mvf "$BASE/CalNyxApp.kt"        "$BASE/core/CalNyxApp.kt"
mvf "$BASE/MainActivity.kt"      "$BASE/core/MainActivity.kt"
mvf "$BASE/ThemePrefs.kt"        "$BASE/core/ThemePrefs.kt"

# --- theme: ui/theme -> theme ---
mvf "$BASE/ui/theme/Color.kt"    "$BASE/theme/Color.kt"
mvf "$BASE/ui/theme/Theme.kt"    "$BASE/theme/Theme.kt"
mvf "$BASE/ui/theme/Type.kt"     "$BASE/theme/Type.kt"

# --- calculator: all calculator screens + their viewmodels ---
for f in NormalCalculatorScreen ScientificCalculatorScreen ConverterCalculatorScreen \
         IctCalculatorScreen NumberSystemCalculatorScreen CalTapeCalculatorScreen HistoryScreen; do
  mvf "$BASE/ui/screens/$f.kt" "$BASE/calculator/$f.kt"
done
for f in CalculatorViewModel CalTapeViewModel HistoryViewModel; do
  mvf "$BASE/viewmodel/$f.kt" "$BASE/calculator/$f.kt"
done

# --- navigation state lives with navigation ---
mvf "$BASE/data/NavState.kt" "$BASE/navigation/NavState.kt"

# --- ball: everything about the floating ball ---
mvf "$BASE/moan/FloatingBallService.kt" "$BASE/ball/FloatingBallService.kt"
mvf "$BASE/moan/BallPanelActivity.kt"   "$BASE/ball/BallPanelActivity.kt"
mvf "$BASE/moan/BallBootReceiver.kt"    "$BASE/ball/BallBootReceiver.kt"

# --- hardcore (power + orientation) ---
mvf "$BASE/moan/OrientationGuard.kt"        "$BASE/hardcore/OrientationGuard.kt"
mvf "$BASE/moan/OrientationService.kt"      "$BASE/hardcore/OrientationService.kt"
mvf "$BASE/moan/OrientationBootReceiver.kt" "$BASE/hardcore/OrientationBootReceiver.kt"
mvf "$BASE/ui/screens/HardcoreScreen.kt"    "$BASE/hardcore/HardcoreScreen.kt"

# --- moan (sound only) ---
mvf "$BASE/ui/screens/MoanScreen.kt" "$BASE/moan/MoanScreen.kt"
# MoanControlPrefs.kt already in moan/, stays there

# --- network ---
mvf "$BASE/moan/WhatsAppHelper.kt"        "$BASE/network/WhatsAppHelper.kt"
mvf "$BASE/moan/WhatsAppOpenerActivity.kt" "$BASE/network/WhatsAppOpenerActivity.kt"
mvf "$BASE/ui/screens/NetworkScreen.kt"    "$BASE/network/NetworkScreen.kt"

# --- applock / blackhole / bluefilm / clipboard / onboarding screens ---
mvf "$BASE/ui/screens/AppLockScreen.kt"         "$BASE/applock/AppLockScreen.kt"
mvf "$BASE/ui/screens/BlackHoleScreen.kt"       "$BASE/blackhole/BlackHoleScreen.kt"
mvf "$BASE/ui/screens/BlackHoleSetupScreen.kt"  "$BASE/blackhole/BlackHoleSetupScreen.kt"
mvf "$BASE/ui/screens/BlueFilmScreen.kt"        "$BASE/bluefilm/BlueFilmScreen.kt"
mvf "$BASE/ui/screens/ClipboardScreen.kt"       "$BASE/clipboard/ClipboardScreen.kt"
mvf "$BASE/ui/screens/OnboardingScreen.kt"      "$BASE/onboarding/OnboardingScreen.kt"

# --- BlueFilm (capital B) -> bluefilm (lowercase, package convention) ---
for f in BlueFilmCrypto BlueFilmService BlueFilmStore ScreenDrawView ScreenRecordService ScreenRecordToolbarActivity; do
  mvf "$BASE/BlueFilm/$f.kt" "$BASE/bluefilm/$f.kt"
done
rmdir "$BASE/BlueFilm" 2>/dev/null || true

echo ""
echo "=== Step 2: fixing package lines, imports and AndroidManifest ==="
python3 - "$BASE" "$MANIFEST" <<'PYEOF'
import os, re, sys
base, manifest = sys.argv[1], sys.argv[2]

# class name -> new package, derived from the file's folder
class_pkg = {}
for root, _, files in os.walk(base):
    for fn in files:
        if fn.endswith(".kt"):
            cls = fn[:-3]
            rel = os.path.relpath(root, base)
            pkg = "com.calnyx.app" if rel == "." else "com.calnyx.app." + rel.replace(os.sep, ".")
            class_pkg[cls] = pkg

def fix_text(text, path):
    # 1) package line = folder's package
    if path.endswith(".kt"):
        d = os.path.dirname(path)
        rel = os.path.relpath(d, base)
        pkg = "com.calnyx.app" if rel == "." else "com.calnyx.app." + rel.replace(os.sep, ".")
        text, n = re.subn(r"^package [A-Za-z0-9_.]+", f"package {pkg}", text, count=1, flags=re.M)
        if n == 0:
            text = f"package {pkg}\n\n" + text
    # 2) any reference com.calnyx.app.<old>.<Class> -> com.calnyx.app.<new>.<Class>
    def repl(m):
        cls = m.group(1)
        if cls in class_pkg:
            return class_pkg[cls] + "." + cls
        return m.group(0)
    text = re.sub(r"com\.calnyx\.app(?:\.[A-Za-z0-9_]+)*\.([A-Z][A-Za-z0-9_]*)", repl, text)
    # 3) Manifest / xml short form only: .<old>.<Class> -> .<new-suffix>.<Class>
    if path.endswith(".xml"):
        def repl_short(m):
            cls = m.group(1)
            if cls in class_pkg:
                suffix = class_pkg[cls][len("com.calnyx.app"):]
                return ("." + suffix.lstrip(".") + "." + cls) if suffix else ("." + cls)
            return m.group(0)
        text = re.sub(r"\.[A-Za-z0-9_.]*?([A-Z][A-Za-z0-9_]*)(?=[\"' ])", repl_short, text)
    return text

changed = []
for root, _, files in os.walk(base):
    for fn in files:
        if fn.endswith(".kt"):
            p = os.path.join(root, fn)
            old = open(p, encoding="utf-8").read()
            new = fix_text(old, p)
            if new != old:
                open(p, "w", encoding="utf-8").write(new); changed.append(p)
for p in [manifest] + [os.path.join(r, f) for r, _, fs in os.walk("app/src/main/res") for f in fs if f.endswith(".xml")]:
    if os.path.exists(p):
        old = open(p, encoding="utf-8").read()
        new = fix_text(old, p)
        if new != old:
            open(p, "w", encoding="utf-8").write(new); changed.append(p)
print(f"fixed {len(changed)} files")
for p in changed: print("  ", p)
print("class -> package:")
for k in sorted(class_pkg): print(f"  {k}: {class_pkg[k]}")
PYEOF

# --- cleanup empty old folders ---
rmdir "$BASE/ui/screens" "$BASE/ui/theme" "$BASE/ui" "$BASE/viewmodel" 2>/dev/null || true

echo ""
echo "=== Step 3: verify ==="
echo "-- leftover old imports (NotepadViewModel ছাড়া কিছু থাকলে সেটা দেখাবে) --"
grep -rn "ui\.screens\|ui\.theme\|app\.viewmodel\|app\.BlueFilm" --include="*.kt" "$BASE" "$MANIFEST" | grep -v "NotepadViewModel" || echo "OK: no old references (NotepadViewModel বাদে)"
echo ""
echo "-- final tree --"
find "$BASE" -name "*.kt" | sort
echo ""
echo "NOTE: viewmodel/NotepadViewModel.kt was NOT moved - Notepad was removed, check if it is still used and delete it if not."
echo "Now run: ktdev build"
