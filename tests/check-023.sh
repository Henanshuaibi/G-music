#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh build.sh
mkdir -p build/check023-classes build/check023-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check023-classes tests/AudioReactionCheck.java tests/FeatureCheck.java tests/DeviceArtPreview.java
java -cp build/classes:build/check023-classes cn.pulse.player.AudioReactionCheck
java -cp build/classes:build/check023-classes cn.pulse.player.FeatureCheck
jar cf build/check023-tests.jar -C build/check023-classes .
[ ! -f build/check023-dex/classes.dex ] || chmod 600 build/check023-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check023-dex build/classes.jar build/check023-tests.jar
chmod 400 build/check023-dex/classes.dex
CLASSPATH="$PWD/build/check023-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player.DeviceArtPreview "$PWD/build/art023"
