#!/usr/bin/env bash
# Fabrique android/out/arbitre-baby.apk sans Gradle (aapt2 + javac + d8 + apksigner).
# Prérequis : JDK 17 et SDK Android (platforms;android-34, build-tools;34.0.0).
# La clé de signature vit hors du dépôt, dans ~/.android/ : la garder, sinon les
# mises à jour de l'appli ne pourront plus s'installer par-dessus l'ancienne.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME="$(ls -d "/c/Program Files/Microsoft/jdk-17"* | head -1)"; fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

KS="$HOME/.android/arbitre-baby.jks"
PASSFILE="$HOME/.android/arbitre-baby.pass"
mkdir -p "$HOME/.android"
if [ ! -f "$KS" ]; then
  head -c 18 /dev/urandom | base64 | tr -d '/+=' > "$PASSFILE"
  keytool -genkeypair -keystore "$KS" -alias arbitre -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$(cat "$PASSFILE")" -keypass "$(cat "$PASSFILE")" -dname "CN=Arbitre Baby, O=Tri5901, C=FR"
fi

rm -rf out && mkdir -p out/res out/gen out/classes out/assets res/mipmap-xxxhdpi
# Icônes et copie de secours de la page, reprises de la racine du dépôt
cp ../icon-192.png res/mipmap-xxxhdpi/ic_launcher.png
cp ../icon-maskable-512.png res/mipmap-xxxhdpi/ic_launcher_fg.png
cp ../index.html ../manifest.json ../icon-192.png out/assets/

"$BT/aapt2" compile --dir res -o out/res/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  -A out/assets --java out/gen out/res/res.zip

javac -source 8 -target 8 -bootclasspath "$JAR" -Xlint:-options -encoding UTF-8 \
  -d out/classes $(find src out/gen -name '*.java')
"$BT/d8.bat" --min-api 26 --lib "$JAR" --output out $(find out/classes -name '*.class')

cp out/base.apk out/unsigned.apk
(cd out && "$BT/aapt" add unsigned.apk classes.dex > /dev/null)
"$BT/zipalign" -f -p 4 out/unsigned.apk out/aligned.apk
"$BT/apksigner.bat" sign --ks "$(cygpath -w "$KS")" --ks-pass "file:$(cygpath -w "$PASSFILE")" --out out/arbitre-baby.apk out/aligned.apk
"$BT/apksigner.bat" verify out/arbitre-baby.apk
ls -l out/arbitre-baby.apk
