#!/usr/bin/env bash
# CalNyx — Third Eye -> Spy rename + new Third Eye folder
# ========================================================
# Run from the PROJECT ROOT (the folder that contains app/).
#   chmod +x rename-spy-thirdeye.sh
#   ./rename-spy-thirdeye.sh
#
# What it does, in order:
#  1. app/.../thirdeye/  ->  app/.../spy/
#     Every file inside gets a Spy name:
#       ThirdEyeScreen.kt              -> SpyScreen.kt
#       ThirdEyeService.kt             -> SpyService.kt
#       ThirdEyeStore.kt               -> SpyStore.kt
#       ThirdEyeCrypto.kt              -> SpyCrypto.kt
#       ScreenDrawView.kt              -> SpyDrawView.kt
#       ScreenRecordService.kt         -> SpyScreenRecordService.kt
#       ScreenRecordToolbarActivity.kt -> SpyScreenRecordToolbarActivity.kt
#     Inside those files: package com.calnyx.app.thirdeye ->
#     com.calnyx.app.spy, class names ThirdEye* -> Spy*, titles
#     "Third Eye" / "Blue Film" -> "Spy", folder "My Blue Film" ->
#     "My Spy" (old "My Blue Film" folder moves to "My Spy", videos
#     are never deleted). The internal storage dir "bluefilm" is
#     KEPT on purpose - renaming it would hide existing videos.
#     NavGraph.kt is updated too: import, Screen object
#     ("blue_film", "Spy", Videocam icon), composable call.
#  2. NEW app/.../thirdeye/ folder is created. Screen Stream files
#     move out of network/ into it:
#       network/ScreenStreamPrefs.kt   -> thirdeye/ScreenStreamPrefs.kt
#       network/ScreenStreamSection.kt -> thirdeye/ScreenStreamSection.kt
#       network/ScreenStreamService.kt -> thirdeye/ScreenStreamService.kt
#     Their package becomes com.calnyx.app.thirdeye.
#     network/ keeps ONLY: NetworkScreen, NetworkSwitcher,
#     WhatsAppHelper, WhatsAppOpenerActivity.
#     The ScreenStreamSection call is removed from NetworkScreen.kt.
#  3. The NEW Third Eye hub screen + Camera Live files are NOT in
#     this script - they come next as updated code files, after
#     this script has run. Same for the new Third Eye entry in
#     NavGraph (menuScreens, last item) and AndroidManifest.
#
# Safe to run once. If a step already happened, it says so and
# stops instead of renaming twice.

set -euo pipefail

BASE="app/src/main/java/com/calnyx/app"

if [ ! -d "$BASE/thirdeye" ]; then
    echo "ERROR: $BASE/thirdeye pelam na. Project root theke chalao (jekhane app/ folder ache)."
    exit 1
fi
if [ -d "$BASE/spy" ]; then
    echo "ERROR: $BASE/spy already ache - script ta bodhoy agei choleche. Ar chalio na."
    exit 1
fi

rename_if_exists() {
    if [ -f "$1" ]; then
        mv "$1" "$2"
        echo "  renamed: $1 -> $2"
    else
        echo "  skip (file nai): $1"
    fi
}

echo "== 1. thirdeye/ -> spy/ =="
mv "$BASE/thirdeye" "$BASE/spy"
cd "$BASE/spy"
rename_if_exists ThirdEyeScreen.kt SpyScreen.kt
rename_if_exists ThirdEyeService.kt SpyService.kt
rename_if_exists ThirdEyeStore.kt SpyStore.kt
rename_if_exists ThirdEyeCrypto.kt SpyCrypto.kt
rename_if_exists ScreenDrawView.kt SpyDrawView.kt
rename_if_exists ScreenRecordService.kt SpyScreenRecordService.kt
rename_if_exists ScreenRecordToolbarActivity.kt SpyScreenRecordToolbarActivity.kt
cd - > /dev/null

echo "== 2. spy/ file gulor vitore naam bodol =="
# SpyStore.kt first: its TWO folder strings swap places -
#   DEFAULT_FOLDER "My Blue Film" -> "My Spy"
#   migration source "My Spy"    -> "My Blue Film"
# (A plain global replace would make both "My Spy" and break the migration.)
if [ -f "$BASE/spy/SpyStore.kt" ]; then
    sed -i \
        -e 's/"My Spy"/"__OLD_SPY_FOLDER__"/g' \
        -e 's/"My Blue Film"/"My Spy"/g' \
        -e 's/"__OLD_SPY_FOLDER__"/"My Blue Film"/g' \
        "$BASE/spy/SpyStore.kt"
    echo "  SpyStore.kt: folder migration ultano holo (My Blue Film -> My Spy)"
fi
# Every spy/*.kt: package + class names + titles.
find "$BASE/spy" -name '*.kt' -exec sed -i \
    -e 's/com\.calnyx\.app\.thirdeye/com.calnyx.app.spy/g' \
    -e 's/ThirdEyeScreen/SpyScreen/g' \
    -e 's/ThirdEyeService/SpyService/g' \
    -e 's/ThirdEyeStore/SpyStore/g' \
    -e 's/ThirdEyeCrypto/SpyCrypto/g' \
    -e 's/ScreenRecordToolbarActivity/SpyScreenRecordToolbarActivity/g' \
    -e 's/ScreenRecordService/SpyScreenRecordService/g' \
    -e 's/ScreenDrawView/SpyDrawView/g' \
    -e 's/ThirdEye/Spy/g' \
    -e 's/thirdEye/spy/g' \
    -e 's/BlueFilm/Spy/g' \
    {} +
# "Blue Film" (space shoho) -> "Spy" - SpyStore.kt baad, karon
# or folder swap ta uporei hoye geche.
find "$BASE/spy" -name '*.kt' ! -name 'SpyStore.kt' -exec sed -i \
    -e 's/Blue Film/Spy/g' \
    {} +
echo "  spy/*.kt: package / class / title bodlano holo"

echo "== 3. NavGraph.kt: Spy rename =="
NAV="$BASE/navigation/NavGraph.kt"
if [ -f "$NAV" ]; then
    sed -i \
        -e 's/import com\.calnyx\.app\.thirdeye\.ThirdEyeScreen/import com.calnyx.app.spy.SpyScreen/' \
        -e 's/Screen\.ThirdEye/Screen.Spy/g' \
        -e 's/object ThirdEye/object Spy/' \
        -e 's/"blue_film", "Third Eye", "Secret camera recorder", Icons\.Default\.Visibility/"blue_film", "Spy", "Secret camera recorder", Icons.Default.Videocam/' \
        -e 's/ThirdEyeScreen(/SpyScreen(/g' \
        -e 's/Third Eye camera recording/Spy camera recording/g' \
        -e 's/Blue Film/Spy/g' \
        "$NAV"
    # Videocam import jog koro (Visibility import er pore), jodi na thake.
    if ! grep -q 'icons.filled.Videocam' "$NAV"; then
        sed -i '/import androidx.compose.material.icons.filled.Visibility/a import androidx.compose.material.icons.filled.Videocam' "$NAV"
    fi
    echo "  NavGraph.kt: Spy rename holo (notun Third Eye entry pore asbe)"
else
    echo "  WARNING: NavGraph.kt pelam na - pore haate thik korte hobe."
fi

echo "== 4. Notun thirdeye/ folder + Screen Stream network theke ber kora =="
mkdir -p "$BASE/thirdeye"
for f in ScreenStreamPrefs.kt ScreenStreamSection.kt ScreenStreamService.kt; do
    rename_if_exists "$BASE/network/$f" "$BASE/thirdeye/$f"
done
find "$BASE/thirdeye" -name '*.kt' -exec sed -i \
    -e 's/^package com\.calnyx\.app\.network$/package com.calnyx.app.thirdeye/' \
    {} +
echo "  Screen Stream ekhon thirdeye/ te, package com.calnyx.app.thirdeye"

echo "== 5. NetworkScreen.kt theke ScreenStreamSection call sorano =="
NET="$BASE/network/NetworkScreen.kt"
if [ -f "$NET" ] && grep -q 'ScreenStreamSection(' "$NET"; then
    python3 - "$NET" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
lines = p.read_text().splitlines(keepends=True)
# Find the "// SCREEN STREAM" comment line, then the
# ScreenStreamSection( call, then its closing "        )" line.
start = next((i for i, l in enumerate(lines) if '// SCREEN STREAM' in l), None)
if start is None:
    start = next(i for i, l in enumerate(lines) if 'ScreenStreamSection(' in l)
call = next(i for i, l in enumerate(lines) if 'ScreenStreamSection(' in l)
end = next(i for i in range(call, len(lines)) if lines[i].rstrip() == '        )')
del lines[start:end + 1]
p.write_text(''.join(lines))
print("  NetworkScreen.kt: ScreenStreamSection call sorano holo")
PY
else
    echo "  NetworkScreen.kt e ScreenStreamSection call nai (agei sorano hoyeche)."
fi

echo ""
echo "== CHECK =="
echo "--- spy/ ---"
ls "$BASE/spy"
echo "--- thirdeye/ (notun) ---"
ls "$BASE/thirdeye"
echo "--- network/ (sudhu Switcher + WhatsApp thakbe) ---"
ls "$BASE/network"
echo ""
if grep -rn 'com\.calnyx\.app\.thirdeye' "$BASE/spy" "$BASE/network" 2>/dev/null; then
    echo "WARNING: spy/ ba network/ te ekhono thirdeye reference ache - uporer line gulo dekho."
else
    echo "OK: spy/ ar network/ te ar thirdeye reference nai."
fi
if grep -q 'ScreenStreamSection' "$NET" 2>/dev/null; then
    echo "WARNING: NetworkScreen.kt te ekhono ScreenStreamSection ache."
else
    echo "OK: NetworkScreen.kt te ScreenStreamSection nai."
fi
echo ""
echo "Ses! Ebar ja baki (pore updated code hisebe asbe):"
echo "  - thirdeye/ThirdEyeScreen.kt (hub: upore Screen Share, niche Camera Live)"
echo "  - thirdeye/CameraLivePrefs.kt, CameraLiveService.kt, CameraLiveSection.kt"
echo "  - NavGraph.kt te notun Third Eye entry (menuScreens er sobar niche)"
echo "  - NetworkScreen.kt er header comment (optional, code e kichu bodlay na)"
echo "  - AndroidManifest (tumi pore dibe, tokhon update hobe)"
