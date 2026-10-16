#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh tests/check-015.sh
mkdir -p build/check022-classes build/check022-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check022-classes tests/DevicePlaybackRecoveryCheck.java tests/DeviceSongImportCheck.java
jar cf build/check022-tests.jar -C build/check022-classes .
[ ! -f build/check022-dex/classes.dex ] || chmod 600 build/check022-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check022-dex build/classes.jar build/check022-tests.jar
chmod 400 build/check022-dex/classes.dex
for gmusic_check in DevicePlaybackRecoveryCheck DeviceSongImportCheck; do
 CLASSPATH="$PWD/build/check022-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player."$gmusic_check"
done
