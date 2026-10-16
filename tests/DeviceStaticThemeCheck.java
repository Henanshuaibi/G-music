package cn.pulse.player;
import android.content.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

public final class DeviceStaticThemeCheck {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static byte[] utf(String s)throws Exception{return s.getBytes("UTF-8");}
 static void write(File file,String value)throws Exception{file.getParentFile().mkdirs();Files.write(file.toPath(),utf(value));}
 static byte[] zip(String... parts)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(int i=0;i<parts.length;i+=2){zip.putNextEntry(new ZipEntry(parts[i]));zip.write(utf(parts[i+1]));zip.closeEntry();}}return out.toByteArray();}
 static void reject(byte[] data,File root,boolean zipped)throws Exception{
  int before=root.list().length;boolean rejected=false;try{if(zipped)ThemePack.install(new ByteArrayInputStream(data),root);else ThemeText.install(new String(data,"UTF-8"),root);}catch(Exception e){rejected=true;}
  check(rejected,"scripts/plugin packages rejected");check(root.list().length==before,"rejected import cleans partial files");
 }
 public static void main(String[] args){File work=new File(args[0],"run-"+System.nanoTime());try{work.mkdirs();run(work);ThemePack.delete(work);System.out.println("PASS: static-only JSON/ZIP, legacy plugin cleanup/default fallback, static theme/data retention, deletion guards and UTF-8 validation");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
 static void run(File work)throws Exception{
  Map<String,DeviceQueueReorderCheck.Prefs> prefs=new HashMap<>();Context c=new ContextWrapper(null){
   public File getFilesDir(){return new File(work,"files");}public File getCacheDir(){return new File(work,"cache");}
   public SharedPreferences getSharedPreferences(String name,int mode){if(!prefs.containsKey(name))prefs.put(name,new DeviceQueueReorderCheck.Prefs());return prefs.get(name).prefs;}
  };
  File root=ThemePack.root(c);ThemePack staticTheme=ThemeText.install(ThemeText.example(),root);ThemePack.select(c,staticTheme);
  String plugin=new JSONObject().put("format","gmusic.plugin.v1").put("name","retired").put("script","main.js").toString();
  reject(utf("while(true){}"),root,false);reject(utf(plugin),root,false);
  reject(utf(new JSONObject(ThemeText.example()).put("script","main.js").toString()),root,false);
  reject(zip("manifest.json",plugin),root,true);
  reject(zip("manifest.json",ThemeText.example(),"main.js","while(true){}"),root,true);
  reject(zip("manifest.json",ThemeText.example(),"../outside.png","invalid"),root,true);
  check(ThemePack.active(c).name.equals(staticTheme.name),"failed imports keep selected theme");
  File old=new File(root,"pack-legacy"),cache=new File(c.getCacheDir(),"theme-preview/pack-old");write(new File(old,"manifest.json"),plugin);write(new File(old,"main.js"),"while(true){}");write(new File(cache,"main.js"),"while(true){}");
  write(new File(c.getCacheDir(),"audio-cache/test"),"keep audio");
  c.getSharedPreferences("favorites",0).edit().putString("items","favorite data").apply();c.getSharedPreferences("MainActivity",0).edit().putString("queue","queue data").apply();c.getSharedPreferences("playback",0).edit().putInt("mode",2).apply();
  ThemePack.removeLegacyPlugins(c);check(!old.exists() && !cache.exists(),"old script pack and previews deleted without executing");check(staticTheme.directory.exists() && ThemePack.active(c).name.equals(staticTheme.name),"static themes/selection retained");
  check(new File(c.getCacheDir(),"audio-cache/test").exists(),"unrelated cache retained");
  write(new File(old,"manifest.json"),plugin);c.getSharedPreferences("extensions",0).edit().putString("active",old.getName()).apply();check(ThemePack.active(c)==null,"selected old plugin immediately falls back to default");
  c.getSharedPreferences("extensions",0).edit().putString("active",old.getName()).apply();ThemePack.removeLegacyPlugins(c);check(!old.exists() && c.getSharedPreferences("extensions",0).getString("active","bad").isEmpty(),"cleanup clears old active plugin");ThemePack.removeLegacyPlugins(c);check(ThemePack.list(c).size()==1,"cleanup safe to repeat");
  for(String name:new String[]{"favorites","MainActivity","playback"})check(prefs.get(name).writes==1,"music data not changed by theme migration");
  ThemePack.select(c,staticTheme);ThemePack extra=ThemeText.install(ThemeText.example(),root);ThemePack.remove(c,extra);check(ThemePack.active(c).name.equals(staticTheme.name),"deleting inactive theme keeps selected theme");ThemePack.remove(c,staticTheme);check(ThemePack.active(c)==null && !staticTheme.directory.exists(),"deleting selected theme defaults and removes files");
  ThemePack outside=ThemeText.install(ThemeText.example(),new File(work,"outside"));boolean guarded=false;try{ThemePack.remove(c,outside);}catch(IOException e){guarded=true;}check(guarded && outside.directory.exists(),"theme delete restricted to installed root");
  check(ThemeText.readText(new ByteArrayInputStream(utf("\ufeff"+ThemeText.example())),ThemeText.MAX_BYTES).equals(ThemeText.example()),"UTF-8 BOM supported");boolean bad=false;try{ThemeText.readText(new ByteArrayInputStream(new byte[]{(byte)255}),ThemeText.MAX_BYTES);}catch(IOException e){bad=true;}check(bad,"malformed UTF-8 rejected");
 }
}
