#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh build.sh
mkdir -p build/check029-classes build/check029-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check029-classes tests/DeviceSongIdentityCheck.java tests/DeviceLibraryCheck.java tests/DevicePlaybackRecoveryCheck.java tests/DeviceDataCheck.java tests/DeviceFavoritesCheck.java tests/DeviceQueueReorderCheck.java tests/DeviceVisualSettingsCheck.java tests/DeviceArtPreview.java
jar cf build/check029-tests.jar -C build/check029-classes .
[ ! -f build/check029-dex/classes.dex ] || chmod 600 build/check029-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check029-dex build/classes.jar build/check029-tests.jar
chmod 400 build/check029-dex/classes.dex
for gmusic_check in DeviceSongIdentityCheck DeviceLibraryCheck DevicePlaybackRecoveryCheck DeviceDataCheck DeviceFavoritesCheck DeviceQueueReorderCheck DeviceVisualSettingsCheck; do
 CLASSPATH="$PWD/build/check029-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player."$gmusic_check" "$PWD/build/check029-results"
done
python3 tests/static-package-check.py
