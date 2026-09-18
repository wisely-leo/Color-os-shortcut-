#!/bin/bash
cd "$(dirname "$0")"
set +e

R="$(pwd)"
STUB="$R/tools/stub"
TOOLS="$R/tools"
LIBS="$R/libs"
TPL="$R/template/TaskviewIconBlur_v70c9.apk"
SRC="$R/src/main/java"
OUT="$R/build"
W="$OUT/work"

MODULE_VER=${MODULE_VER:-17}
APP_LABEL=${APP_LABEL:-ShortcutBlur}
OUT_APK=${OUT_APK:-$OUT/ShortcutBlur_v${MODULE_VER}.apk}

JAVAC=${JAVAC:-javac}

echo "=========================================="
echo " ShortcutBlur build"
echo " ver    : $MODULE_VER"
echo " label  : $APP_LABEL"
echo " javac  : $($JAVAC -version 2>&1)"
echo " output : $OUT_APK"
echo "=========================================="

for f in "$TPL" "$LIBS/libxposed-api-102.jar" "$LIBS/r8.jar" "$LIBS/apksig.jar" "$LIBS/bcpkix-jdk18on-1.78.1.jar" "$LIBS/bcprov-jdk18on-1.78.1.jar"; do
  if [ ! -e "$f" ]; then echo "MISSING: $f"; exit 1; fi
done

rm -rf "$W"
mkdir -p "$W/classes" "$W/dex" "$W/pkg" "$W/stubsrc"

echo
echo "=== [1/6] stub ==="
cp -r "$STUB"/* "$W/stubsrc/"
find "$W/stubsrc" -name '*.java' > "$W/stub_src.txt"
wc -l < "$W/stub_src.txt"
$JAVAC -nowarn -source 8 -target 8 -encoding UTF-8 -d "$W/classes" @"$W/stub_src.txt" 2>&1 | tail -10
echo "stub_exit=$?"

echo
echo "=== [2/6] module source ==="
find "$SRC" -name '*.java' > "$W/main_src.txt"
cat "$W/main_src.txt"
$JAVAC -nowarn -source 8 -target 8 -encoding UTF-8 \
  -cp "$W/classes:$LIBS/libxposed-api-102.jar" -d "$W/classes" @"$W/main_src.txt" 2>&1 | tail -40
echo "main_exit=$?"
echo -n "module classes: "; find "$W/classes" -name '*.class' | grep -vc '/android/\|/io/github/'

echo
echo "=== [3/6] d8 ==="
java -cp "$LIBS/r8.jar" com.android.tools.r8.D8 \
  --min-api 34 --lib "$LIBS/r8.jar" --output "$W/dex" \
  $(find "$W/classes" -name '*.class' | grep -v '/android/\|/io/github/') 2>&1 | tail -20
echo -n "dex size: "; stat -c %s "$W/dex/classes.dex" 2>/dev/null

echo
echo "=== [4/6] pack ==="
python3 "$TOOLS/build/sc_pack_prep.py" "$TPL" "$W/pkg" "$W/stage.apk" 2>&1 | tail -8
cp "$W/dex/classes.dex" "$W/pkg/classes.dex"

echo
echo "=== [5/6] manifest ==="
python3 "$TOOLS/build/sc_patch_version.py" "$W/pkg/AndroidManifest.xml" "$MODULE_VER" "$MODULE_VER" "$APP_LABEL" 2>&1 | tail -8
python3 - <<PY
import sys, os
sys.path.insert(0, "$TOOLS/build")
from sc_pack_prep import pack_aligned
out = pack_aligned(r"$W/pkg", r"$W/unsigned.apk")
print('unsigned ok', os.path.getsize(out))
PY

echo
echo "=== [6/6] sign ==="
SG="$TOOLS/signer"
KS="$SG/debug.keystore"
CP="$LIBS/apksig.jar:$LIBS/bcpkix-jdk18on-1.78.1.jar:$LIBS/bcprov-jdk18on-1.78.1.jar"
mkdir -p "$W/signerclasses"
$JAVAC -nowarn -source 8 -target 8 -encoding UTF-8 -cp "$CP" \
  -d "$W/signerclasses" "$SG/SignApk.java" "$SG/VerifyApk.java" 2>&1 | tail -5
if [ ! -e "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 2>&1 | tail -2
fi
CP="$W/signerclasses:$CP"
java -cp "$CP" SignApk "$W/unsigned.apk" "$W/signed.apk" "$KS" android androiddebugkey 2>&1 | tail -3
java -cp "$CP" VerifyApk "$W/signed.apk" 2>&1 | tail -5

mkdir -p "$(dirname "$OUT_APK")"
cp "$W/signed.apk" "$OUT_APK"
echo
echo "=== done ==="
ls -l "$OUT_APK"
echo -n "MD5="; md5sum "$OUT_APK" | cut -d' ' -f1