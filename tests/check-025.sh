#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh tests/check-024.sh
mkdir -p build/check025-classes build/check025-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check025-classes tests/DeviceArtPreview.java tests/DevicePlaybackRecoveryCheck.java tests/DeviceVisualSettingsCheck.java
jar cf build/check025-tests.jar -C build/check025-classes .
[ ! -f build/check025-dex/classes.dex ] || chmod 600 build/check025-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check025-dex build/classes.jar build/check025-tests.jar
chmod 400 build/check025-dex/classes.dex
CLASSPATH="$PWD/build/check025-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player.DeviceArtPreview "$PWD/build/art025"
CLASSPATH="$PWD/build/check025-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player.DeviceVisualSettingsCheck "$PWD/build/art025"
