#!/usr/bin/env bash
# CalNyx - reorganize-এর পর ভাঙা import ঠিক করার স্ক্রিপ্ট
# Run from project root (CalNyx folder):
#   bash fix-imports.sh
#   ktdev build
#
# কেন দরকার: reorganize.sh শুধু ফাইলের নাম দিয়ে import বদলেছিল।
# কিন্তু আসল কোডে অনেক জিনিস ফাইলের নামের সঙ্গে মেলে না:
#  - MoanControlPrefs / OrientationService: আগে সব moan/ প্যাকেজে ছিল,
#    তাই import ছাড়াই ব্যবহার হতো; এখন ball/hardcore/moan আলাদা।
#  - CalNyxRed / CalNyxGreen / SciAmber...: Color.kt-র ভেতরের val,
#    CalNyxTheme: Theme.kt-র ভেতরের fun — ফাইলের নাম নয়।
#  - SheetEntity / NavStateDatabase / NavStateEntity: Entities.kt /
#    NavState.kt-র ভেতরে একাধিক class, আর NavState.kt data -> navigation-এ গেছে।
# এই স্ক্রিপ্ট সব .kt ফাইল পড়ে প্রতিটা top-level symbol (class/object/
# fun/val/...) কোন প্যাকেজে আছে তার ম্যাপ বানায়, তারপর
#  1) ভুল প্যাকেজের পুরনো import ঠিক করে,
#  2) import ছাড়া ব্যবহৃত symbol-এর import যোগ করে,
#  3) কোডের ভেতরের com.calnyx.app.ui.theme.CalNyxRed ধরনের
#     পুরো পথও ঠিক করে।
set -e
BASE="app/src/main/java/com/calnyx/app"
if [ ! -d "$BASE" ]; then
  echo "ERROR: $BASE not found. Run this from the CalNyx project root."
  exit 1
fi
python3 - "$BASE" <<'PYEOF'
import os, re, sys
base = sys.argv[1]

# old package prefix -> new package (wildcard import-এর জন্য)
PKG_REMAP = {
    "com.calnyx.app.ui.theme": "com.calnyx.app.theme",
    "com.calnyx.app.ui.screens": None,  # screens split across packages; symbol map handles it
    "com.calnyx.app.viewmodel": "com.calnyx.app.calculator",
    "com.calnyx.app.BlueFilm": "com.calnyx.app.bluefilm",
}

# 1) symbol -> package map: only top-level declarations (column 0)
decl_re = re.compile(
    r"^(?:public\s+|private\s+|internal\s+|open\s+|abstract\s+|sealed\s+|data\s+)*"
    r"(?:class|object|interface|fun|val|var|typealias|enum\s+class|annotation\s+class)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)
symbol_pkg, symbol_file, duplicates = {}, {}, set()
files = []
for root, _, fns in os.walk(base):
    for fn in sorted(fns):
        if fn.endswith(".kt"):
            files.append(os.path.join(root, fn))
for p in files:
    rel = os.path.relpath(os.path.dirname(p), base)
    pkg = "com.calnyx.app" if rel == "." else "com.calnyx.app." + rel.replace(os.sep, ".")
    text = open(p, encoding="utf-8").read()
    for m in decl_re.finditer(text):
        sym = m.group(1)
        if sym in symbol_pkg and symbol_pkg[sym] != pkg:
            duplicates.add(sym)
        elif sym not in symbol_pkg:
            symbol_pkg[sym] = pkg; symbol_file[sym] = p
for d in sorted(duplicates):
    print(f"WARN: ambiguous symbol '{d}' in multiple packages - will NOT auto-import it")

def pkg_of(path):
    rel = os.path.relpath(os.path.dirname(path), base)
    return "com.calnyx.app" if rel == "." else "com.calnyx.app." + rel.replace(os.sep, ".")

changed_files, added_total = [], 0
for p in files:
    text = open(p, encoding="utf-8").read()
    lines = text.split("\n")
    mypkg = pkg_of(p)
    local = {m.group(1) for m in decl_re.finditer(text)}
    # existing imports
    imported, wildcards = {}, set()
    for ln in lines:
        m = re.match(r"\s*import\s+([A-Za-z0-9_.]+)(\.\*)?\s*$", ln)
        if m:
            if m.group(2): wildcards.add(m.group(1))
            else: imported[m.group(1).rsplit(".", 1)[-1]] = m.group(1).rsplit(".", 1)[0]
    new_lines, file_changed = [], False
    for ln in lines:
        m = re.match(r"(\s*import\s+)([A-Za-z0-9_.]+)(\.\*)?(\s*)$", ln)
        if m and m.group(2).startswith("com.calnyx.app"):
            full = m.group(2)
            if m.group(3):  # wildcard
                if full in PKG_REMAP and PKG_REMAP[full]:
                    ln = f"{m.group(1)}{PKG_REMAP[full]}.*"; file_changed = True
            else:
                pkg_part, sym = full.rsplit(".", 1)
                if sym in symbol_pkg and symbol_pkg[sym] != pkg_part and sym not in duplicates:
                    ln = f"{m.group(1)}{symbol_pkg[sym]}.{sym}"; file_changed = True
                    imported[sym] = symbol_pkg[sym]
        new_lines.append(ln)
    text = "\n".join(new_lines)
    # 2) fully-qualified usages in code: com.calnyx.app.<old>.<Sym> -> correct package
    def repl_fq(m):
        sym = m.group(1)
        if sym in symbol_pkg and sym not in duplicates:
            return symbol_pkg[sym] + "." + sym
        return m.group(0)
    new_text = re.sub(r"com\.calnyx\.app(?:\.[A-Za-z0-9_]+)*\.([A-Z][A-Za-z0-9_]*)", repl_fq, text)
    if new_text != text: text, file_changed = new_text, True
    # 3) bare usages needing an import
    #    scan body without import/package lines
    body_lines = [ln for ln in text.split("\n") if not ln.strip().startswith(("import ", "package "))]
    body = "\n".join(body_lines)
    needed = []
    for sym, pkg in symbol_pkg.items():
        if sym in local or sym in duplicates or pkg == mypkg: continue
        if sym in imported or pkg in wildcards: continue
        if re.search(r"(?<![\w.])" + re.escape(sym) + r"\b", body):
            needed.append(sym)
    if needed:
        needed.sort()
        imports_to_add = [f"import {symbol_pkg[s]}.{s}" for s in needed]
        out = text.split("\n")
        last_import = max([i for i, ln in enumerate(out) if ln.strip().startswith("import ")], default=-1)
        if last_import >= 0:
            out[last_import+1:last_import+1] = imports_to_add
        else:
            pkg_idx = next((i for i, ln in enumerate(out) if ln.startswith("package ")), 0)
            out[pkg_idx+1:pkg_idx+1] = [""] + imports_to_add
        text = "\n".join(out); file_changed = True; added_total += len(needed)
        print(f"{os.path.relpath(p, base)}: +import {', '.join(needed)}")
    if file_changed:
        open(p, "w", encoding="utf-8").write(text); changed_files.append(p)

print(f"\nchanged {len(changed_files)} files, added {added_total} imports")
print("symbol map (sample):")
for s in ["MoanControlPrefs","OrientationService","OrientationGuard","CalNyxTheme","CalNyxRed","CalNyxGreen","SheetEntity","NavStateDatabase","NavStateEntity"]:
    if s in symbol_pkg: print(f"  {s}: {symbol_pkg[s]}")
    else: print(f"  {s}: NOT FOUND in any .kt file - check spelling / file present")
PYEOF

echo ""
echo "=== verify ==="
echo "-- old packages left (should be empty) --"
if [ -f app/src/main/AndroidManifest.xml ]; then grep -rn "com\.calnyx\.app\.ui\.\|com\.calnyx\.app\.BlueFilm\|com\.calnyx\.app\.viewmodel" --include="*.kt" "$BASE" app/src/main/AndroidManifest.xml || echo "OK: no old package references"; else grep -rn "com\.calnyx\.app\.ui\.\|com\.calnyx\.app\.BlueFilm\|com\.calnyx\.app\.viewmodel" --include="*.kt" "$BASE" || echo "OK: no old package references"; fi
echo ""
echo "-- still-bare known symbols without import (manual check) --"
grep -rn "MoanControlPrefs\|OrientationService\|CalNyxTheme\|CalNyxRed" --include="*.kt" "$BASE" | grep -v "^.*import " | head -n 20
echo ""
echo "Now run: ktdev build"
echo "If errors remain, paste the NEW build log - the remaining ones will be a different (smaller) set."
