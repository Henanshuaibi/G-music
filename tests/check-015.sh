#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh build.sh
mkdir -p build/check015-classes build/check015-dex
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check015-classes tests/DeviceThemeTextCheck.java tests/DeviceStaticThemeCheck.java tests/DevicePlaybackControlsCheck.java tests/DeviceQueueGestureCheck.java tests/DeviceQueueReorderCheck.java tests/DeviceDataCheck.java tests/DeviceFavoritesCheck.java tests/AudioReactionCheck.java tests/FeatureCheck.java tests/DragAutoScrollCheck.java
jar cf build/check015-tests.jar -C build/check015-classes .
[ ! -f build/check015-dex/classes.dex ] || chmod 600 build/check015-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check015-dex build/classes.jar build/check015-tests.jar
chmod 400 build/check015-dex/classes.dex
for gmusic_check in DeviceThemeTextCheck DeviceStaticThemeCheck DevicePlaybackControlsCheck DeviceQueueGestureCheck DeviceQueueReorderCheck DeviceDataCheck DeviceFavoritesCheck; do
 CLASSPATH="$PWD/build/check015-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player."$gmusic_check" "$PWD/build/check015-results"
done
for gmusic_check in DragAutoScrollCheck AudioReactionCheck FeatureCheck; do
 java -cp build/classes:build/check015-classes cn.pulse.player."$gmusic_check"
done
python3 tests/static-package-check.py
