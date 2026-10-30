#!/usr/bin/env bash
# Builds launcher/build/dumbtv-home.apk from the bare SDK tools: no Gradle, no libraries.
# Needs a JDK 17+ and an Android SDK with platforms;android-$MIN_SDK and any build-tools.
#   MIN_SDK=25 (default)  one APK for Fire OS 6, 7 and 8: the published release. Compiling
#                         against android-25 makes javac reject any android.* API Fire OS 6
#                         lacks; java.* calls are checked against JDK 8 by --release 8, so keep
#                         them to what Android 7.1 has. Fire OS 7/8 see no permissions: the
#                         Keeper's three are capped at maxSdkVersion 27.
#   MIN_SDK=28            Android 9+ only, as releases up to 1.1 were
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# shellcheck disable=SC1091
source version.env
MIN_SDK=${MIN_SDK:-25}

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
WINDOWS=0
command -v cygpath >/dev/null && WINDOWS=1
# Git Bash: native tools need C:/... paths, including the ones inside javac's and d8's @files.
winpath() { if [[ $WINDOWS == 1 ]]; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
if [[ -z $SDK ]]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" \
           ${LOCALAPPDATA:+"$(winpath "$LOCALAPPDATA")/Android/Sdk"}; do
    if [[ -f $d/platforms/android-$MIN_SDK/android.jar ]]; then SDK=$d; break; fi
  done
fi
JAR=$SDK/platforms/android-$MIN_SDK/android.jar
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)
[[ -f $JAR ]] || { echo "error: $JAR missing — sdkmanager 'platforms;android-$MIN_SDK'" >&2; exit 1; }
[[ -x $BT/aapt2 ]] || { echo "error: no build-tools in $SDK — sdkmanager 'build-tools;34.0.0'" >&2; exit 1; }

JDK17_PATHS=(/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /usr/lib/jvm/java-17-openjdk)
if [[ $WINDOWS == 1 ]]; then
  for j in "$(winpath "${LOCALAPPDATA:-}")"/Programs/jdk17/jdk-17* "C:/Program Files/Microsoft"/jdk-17*; do
    [[ -x $j/bin/javac ]] && JDK17_PATHS+=("$j")
  done
fi
[[ -n ${JAVA_HOME:-} ]] && JAVA_HOME=$(winpath "$JAVA_HOME")
javac_major() { "$1/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p'; }

if [[ -z ${JAVA_HOME:-} || ! -x ${JAVA_HOME:-}/bin/javac ]]; then
  for j in "${JDK17_PATHS[@]}" /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
           "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [[ -n $j && -x $j/bin/javac ]]; then JAVA_HOME=$j; break; fi
  done
fi
# build-tools 34's bundled d8 threw an internal NullPointerException dexing an anonymous inner
# class (Keeper$1) compiled by JDK 27, even with --release 8. JDK 17 builds it cleanly. Only 27
# was seen failing; 21 is a conservative cut-off, since macOS's java_home and a Linux distro's
# default javac both commonly land on something newer than 17 now.
if [[ -z ${JAVA_HOME:-} ]] || [[ $(javac_major "$JAVA_HOME") -ge 21 ]]; then
  for j in "${JDK17_PATHS[@]}"; do [[ -x $j/bin/javac ]] && { JAVA_HOME=$j; break; }; done
fi
[[ -x ${JAVA_HOME:-}/bin/javac ]] || {
  echo "error: no JDK found — brew install openjdk@17 (macOS) or pacman -S jdk17-openjdk (Arch)" >&2
  exit 1
}
if [[ $(javac_major "$JAVA_HOME") -ge 21 ]]; then
  echo "warning: $JAVA_HOME is JDK $(javac_major "$JAVA_HOME"); build-tools' d8 crashed on JDK 27 output — install a JDK 17 if this fails" >&2
fi
export JAVA_HOME

KEYSTORE=${DUMBTV_KEYSTORE:-$PWD/release.keystore}
KEYPASS=${DUMBTV_KEYPASS:-dumbtvhome}
if [[ ! -f $KEYSTORE ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias home -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=dumbtv home" >/dev/null
  echo "created signing key $KEYSTORE (keep it: updates must be signed with the same key)"
fi

WORK=$(winpath "$(mktemp -d "${TMPDIR:-/tmp}/dumbtv-home.XXXXXX")")
trap 'rm -rf "$WORK"' EXIT

# build-tools on Windows ship d8 and apksigner as .bat wrappers, which bash cannot run.
d8() {
  if [[ -x $BT/d8 ]]; then "$BT/d8" "$@"
  else "$JAVA_HOME/bin/java" -Xmx1024M -Xss1m -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 "$@"; fi
}
apksigner() {
  if [[ -x $BT/apksigner ]]; then "$BT/apksigner" "$@"
  else "$JAVA_HOME/bin/java" -jar "$BT/lib/apksigner.jar" "$@"; fi
}
# Git Bash has no zip; Python's zipfile appends classes.dex the same way.
add_dex() {
  if command -v zip >/dev/null; then (cd "$WORK" && zip -q -X app.apk classes.dex)
  else python -c 'import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a", zipfile.ZIP_DEFLATED) as z: z.write(sys.argv[2], "classes.dex")' \
    "$WORK/app.apk" "$WORK/classes.dex"; fi
}
mkdir -p "$WORK/gen" "$WORK/classes" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
ASSETS=()
[[ -d assets ]] && ASSETS=(-A assets)
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" ${ASSETS[@]+"${ASSETS[@]}"} \
  --min-sdk-version "$MIN_SDK" --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Xlint:-options -Werror -encoding UTF-8 \
  -classpath "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
d8 --release --min-api "$MIN_SDK" --lib "$JAR" --output "$WORK" @"$WORK/classes.txt"
add_dex

"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out build/dumbtv-home.apk "$WORK/aligned.apk"
apksigner verify build/dumbtv-home.apk

perms=$("$BT/aapt2" dump permissions build/dumbtv-home.apk | grep -c 'uses-permission' || true)
printf 'built build/dumbtv-home.apk  %s bytes  %s uses-permission entries\n' \
  "$(wc -c < build/dumbtv-home.apk | tr -d ' ')" "$perms"
