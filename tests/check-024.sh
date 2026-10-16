#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh tests/check-022.sh
mkdir -p build/check024-classes build/check024-dex build/check024-audio
python3 - <<'PY'
import wave,math,array
for rate in (44100,96000,192000):
    data=array.array('h',(int(19000*math.sin(2*math.pi*93*i/rate)) for i in range(rate)))
    with wave.open('build/check024-audio/tone-'+str(rate)+'.wav','wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate)
        for _ in range(18):f.writeframesraw(data.tobytes())
PY
javac --release 8 -encoding UTF-8 -classpath tools/android.jar:build/classes -d build/check024-classes tests/DeviceAnalyzerContinuityCheck.java
jar cf build/check024-tests.jar -C build/check024-classes .
[ ! -f build/check024-dex/classes.dex ] || chmod 600 build/check024-dex/classes.dex
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/check024-dex build/classes.jar build/check024-tests.jar
chmod 400 build/check024-dex/classes.dex
CLASSPATH="$PWD/build/check024-dex/classes.dex" /system/bin/app_process /system/bin cn.pulse.player.DeviceAnalyzerContinuityCheck "$PWD/build/check024-audio"
