package cn.pulse.player;
import android.graphics.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
public final class DeviceThemeTextCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static File work;
 static ThemePack importText(String code)throws Exception{return ThemeText.install(code,new File(work,"themes"));}
 static void reject(String code,String reason)throws Exception{File root=new File(work,"themes");int before=root.list()==null?0:root.list().length;boolean rejected=false;try{importText(code);}catch(Exception e){rejected=true;}check(rejected,reason);check((root.list()==null?0:root.list().length)==before,"failed import leaves no temporary pack");}
 public static void main(String[] args){try{work=new File(args[0],"run-"+System.nanoTime());work.mkdirs();run();ThemePack.delete(work);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run()throws Exception{
  ThemePack cat=importText(ThemeText.example());check(cat.accent==Color.parseColor("#E8E8E8"),"static theme");check(cat.backgroundColor==Color.parseColor("#080808"),"background field");android.content.Context context=new android.content.ContextWrapper(null){public File getFilesDir(){return work;}};File savedRoot=ThemePack.root(context);check(ThemePack.list(context).isEmpty(),"preview not installed");ThemePack saved=ThemeText.save(cat,savedRoot);check(saved.directory.exists() && !cat.directory.exists(),"apply saves preview and removes staging directory");check(ThemePack.list(context).size()==1,"saved theme listed");
  ThemePack restored=importText(ThemeText.encode(saved));check(restored.name.equals(saved.name),"copy/paste roundtrip");ThemePack plain=importText(ThemeText.encode(null));check(plain.accent==0xffeeeeee,"copy default preserves default appearance without adding pet");
  ThemePack fenced=importText("```json\n"+ThemeText.example()+"\n```");check(fenced.accent==cat.accent,"markdown code fence paste");ThemePack.delete(fenced.directory);
  Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.CYAN);ByteArrayOutputStream png=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,png);String data="data:image/png;base64,"+Base64.getEncoder().encodeToString(png.toByteArray());
  JSONObject m=new JSONObject(ThemeText.example()).put("wallpaper","wall.png").put("background","#101820").put("pet",new JSONObject().put("frames",new JSONArray().put("cat/frame.png")).put("sizeDp",80).put("frameMs",120)).put("assets",new JSONObject().put("wall.png",data).put("cat/frame.png",data));
  ThemePack picture=importText(m.toString());check(picture.background()!=null,"legacy wallpaper loads while pet is ignored");check(!ThemeText.encode(picture).contains("\"pet\""),"export drops legacy pet");ThemePack portable=importText(ThemeText.encode(picture));check(Arrays.equals(java.nio.file.Files.readAllBytes(picture.asset("wall.png").toPath()),java.nio.file.Files.readAllBytes(portable.asset("wall.png").toPath())),"embedded PNG survives export/reimport");check(new File(portable.directory,"manifest.json").length()<65536,"inline assets stripped from persisted manifest");
  reject("not a theme","invalid JSON");reject("{}","wrong format");reject(new JSONObject(ThemeText.example()).put("accent","red").toString(),"invalid accent");reject(new JSONObject(ThemeText.example()).put("background","bad").toString(),"invalid background");reject(new JSONObject(ThemeText.example()).put("wallpaper","https://example.test/a.png").toString(),"no implicit remote downloads");
  reject(new JSONObject(m.toString()).put("assets",new JSONObject().put("../outside.png",data)).toString(),"path traversal");reject(new JSONObject(m.toString()).put("assets",new JSONObject().put("script.js","console.log(1)")).toString(),"script in static theme");reject(new JSONObject(m.toString()).put("assets",new JSONObject().put("wall.png","data:image/png;base64,bad!" )).toString(),"bad base64");reject(new JSONObject(m.toString()).put("assets",new JSONObject().put("wall.png","data:image/png;base64,"+Base64.getEncoder().encodeToString("invalid image".getBytes("UTF-8")))).toString(),"bad image");reject(new JSONObject(ThemeText.example()).put("assets",12).toString(),"invalid assets object");
  char[] large=new char[ThemeText.MAX_BYTES+1];Arrays.fill(large,'x');reject(new String(large),"oversized clipboard input");
  ByteArrayOutputStream zipBytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(zipBytes)){zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(ThemeText.example().getBytes("UTF-8"));zip.closeEntry();}ThemePack legacy=ThemePack.install(new ByteArrayInputStream(zipBytes.toByteArray()),savedRoot);check(legacy.name.equals(cat.name),"ZIP import remains compatible");
  try(RandomAccessFile file=new RandomAccessFile(picture.asset("wall.png"),"rw")){file.setLength(ThemeText.MAX_BYTES);}boolean tooBig=false;try{ThemeText.encode(picture);}catch(IOException e){tooBig=true;}check(tooBig,"oversized image export rejected");
  System.out.println("PASS: text themes, default/static themes, import isolation, saved theme listing, Markdown paste, legacy wallpaper roundtrip, malformed/oversized/unsafe inputs rejected with cleanup, legacy ZIP compatibility");
 }
}
