#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")"
gmusic_keystore="tools/pulse.keystore"
if [ ! -f "$gmusic_keystore" ] && [ -f "$HOME/.local/share/gmusic-signing/pulse.keystore" ]; then
 gmusic_keystore="$HOME/.local/share/gmusic-signing/pulse.keystore"
fi
if [ ! -f "$gmusic_keystore" ]; then
 echo '缺少原签名文件，已停止构建，避免生成无法覆盖更新的安装包。' >&2
 exit 1
fi
gmusic_password_file="${GMUSIC_SIGNING_PASSWORD_FILE:-tools/signing-password}"
if [ ! -f "$gmusic_password_file" ] && [ -z "${GMUSIC_SIGNING_PASSWORD_FILE:-}" ] && [ -f "$HOME/.local/share/gmusic-signing/signing-password" ]; then
 gmusic_password_file="$HOME/.local/share/gmusic-signing/signing-password"
fi
if [ ! -r "$gmusic_password_file" ]; then
 echo '缺少本地签名密码文件。请配置 tools/signing-password 或 GMUSIC_SIGNING_PASSWORD_FILE，勿将密码提交到仓库。' >&2
 exit 1
fi
gmusic_expected_cert=$(cat signing-certificate.sha256)
gmusic_actual_cert=$(keytool -exportcert -keystore "$gmusic_keystore" -storepass:file "$gmusic_password_file" -alias pulse | sha256sum | cut -d ' ' -f 1)
if [ "$gmusic_actual_cert" != "$gmusic_expected_cert" ]; then
 echo '签名与已发布版本不一致，已停止构建。请恢复原签名文件。' >&2
 exit 1
fi
mkdir -p build/generated build/classes build/dex
rm -f build/classes/cn/pulse/player/*.class
aapt2 compile --dir res -o build/res.zip
aapt2 link -I tools/android-res.jar --manifest AndroidManifest.xml --java build/generated -o build/resources.apk build/res.zip
javac --release 8 -encoding UTF-8 -classpath tools/android.jar -d build/classes src/cn/pulse/player/*.java build/generated/cn/pulse/player/R.java
jar cf build/classes.jar -C build/classes .
java -Xmx512m -cp tools/r8.jar com.android.tools.r8.D8 --lib tools/android.jar --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
jar uf build/unsigned.apk -C build/dex classes.dex
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$gmusic_keystore" --ks-pass "file:$gmusic_password_file" --out build/G-music-0.27.apk build/aligned.apk
apksigner verify --verbose build/G-music-0.27.apk
