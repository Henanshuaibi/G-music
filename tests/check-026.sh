#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh tests/check-025.sh
mkdir -p build/check026-classes build/check026-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check026-classes tests/DevicePlaybackRecoveryCheck.java tests/DeviceOptimizationCheck.java
jar cf build/check026-tests.jar -C build/check026-classes .
[ ! -f build/check026-dex/classes.dex ] || chmod 600 build/check026-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check026-dex build/classes.jar build/check026-tests.jar
chmod 400 build/check026-dex/classes.dex
CLASSPATH="$PWD/build/check026-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player.DeviceOptimizationCheck "$PWD/build/check024-audio/tone-44100.wav"
