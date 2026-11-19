#!/bin/sh
# Builds out/salon.apk with plain SDK tools (no Gradle). Run from Git Bash (Windows) or any POSIX shell: sh build.sh
# Needs JAVA_HOME (JDK 17+) and ANDROID_HOME or ANDROID_SDK_ROOT (platform 34, build-tools 34.0.0), plus Python 3.
# They can come from the environment or from a local.env file next to this script (see local.env.example).
set -e
cd "$(dirname "$0")"
[ -f local.env ] && . ./local.env
die() { echo "build.sh: $*" >&2; exit 1; }
[ -n "$JAVA_HOME" ] || die "JAVA_HOME is not set (point it at a JDK 17+). See local.env.example."
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
[ -n "$SDK" ] || die "ANDROID_HOME (or ANDROID_SDK_ROOT) is not set. See local.env.example."
BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
[ -d "$BT" ] || die "missing $BT (install build-tools 34.0.0)"
[ -f "$JAR" ] || die "missing $JAR (install platform android-34)"
export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"
tool() { for x in "$BT/$1.exe" "$BT/$1.bat" "$BT/$1"; do [ -f "$x" ] && { echo "$x"; return; }; done; die "missing $1 in $BT"; }
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; } # Windows tools want Windows paths
PY=; for p in python3 python; do "$p" -c "" >/dev/null 2>&1 && { PY=$p; break; }; done # skips the Windows Store python3 stub
[ -n "$PY" ] || die "Python 3 is needed to put classes.dex into the APK"
AAPT2=$(tool aapt2); D8=$(tool d8); ZIPALIGN=$(tool zipalign); APKSIGNER=$(tool apksigner)

rm -rf out; mkdir -p out/classes
"$AAPT2" compile --dir res -o out/res.zip
"$AAPT2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml -A assets out/res.zip --java out/gen
javac -source 17 -target 17 -encoding UTF-8 -classpath "$JAR" -d out/classes $(find src out/gen -name '*.java')
jar cf out/classes.jar -C out/classes . && "$D8" --min-api 26 --lib "$(native "$JAR")" --output "$(native out)" "$(native out/classes.jar)"
cp out/base.apk out/unsigned.apk && "$PY" -I -c "import zipfile;zipfile.ZipFile('out/unsigned.apk','a').write('out/classes.dex','classes.dex')"
"$ZIPALIGN" -f 4 out/unsigned.apk out/aligned.apk

# Signing: KEYSTORE + KEYSTORE_PASS (alias "salon") if given; otherwise a local debug keystore with a fixed test password.
if [ -n "$KEYSTORE_PASS" ]; then KS=${KEYSTORE:-.keystore/salon.keystore}; else KS=.keystore/debug.keystore; KEYSTORE_PASS=android; fi
export KEYSTORE_PASS # apksigner reads it via env:, so it stays off the command line
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -alias salon -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=Salon" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$(native "$KS")" --ks-key-alias salon --ks-pass env:KEYSTORE_PASS --out out/salon.apk out/aligned.apk
ls -la out/salon.apk
