from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET
version = ET.parse('AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
with zipfile.ZipFile(f'build/G-music-{version}.apk') as apk:
    names = apk.namelist()
    assert not any(n.endswith('.js') or 'sakura' in n or 'plugin_host' in n for n in names), names
    dex = apk.read('classes.dex')
    for retired in (b'Landroid/webkit/', b'BackgroundPluginView', b'PluginPage', b'ThemePreview', '体验樱花'.encode(), '预览'.encode()):
        assert retired not in dex, retired
for name in ('BackgroundPluginView.java', 'PluginPage.java', 'ThemePreview.java'):
    assert not list(Path('src').rglob(name)), name
print('PASS: APK contains no WebView references, dynamic script resources or theme preview entry')
