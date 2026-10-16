package cn.pulse.player;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Offline static colors and wallpapers. */
public final class ThemePack {
 public final File directory;public final String name,wallpaper;public final int accent,backgroundColor;
 private ThemePack(File directory,JSONObject m)throws Exception{
  String format=m.optString("format");if(!"gmusic.theme.v1".equals(format) || m.has("script"))throw new IOException("仅支持静态主题 gmusic.theme.v1");this.directory=directory;name=m.optString("name","").trim();if(name.isEmpty() || name.length()>48)throw new IOException("扩展名称需为 1–48 个字符");
  String color=m.optString("accent","#C6EBE9");if(!color.matches("#[0-9a-fA-F]{6}"))throw new IOException("accent 需为 #RRGGBB");accent=Color.parseColor(color);String bg=m.optString("background","#080808");if(!bg.matches("#[0-9a-fA-F]{6}"))throw new IOException("background 需为 #RRGGBB");backgroundColor=Color.parseColor(bg);wallpaper=m.optString("wallpaper","");if(!wallpaper.isEmpty())checkImage(asset(wallpaper),16000000);
  // Ignore retired pet fields so old colors and wallpapers remain usable.

 }
 private static void checkImage(File file,long max)throws IOException{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<1 || o.outHeight<1 || (long)o.outWidth*o.outHeight>max)throw new IOException("图片无效或尺寸过大："+file.getName());}
 public File asset(String path)throws IOException{if(!safePath(path))throw new IOException("无效的资源路径");File f=new File(directory,path);if(!f.isFile() || !f.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("扩展缺少资源："+path);return f;}
 static boolean safePath(String path){if(path.isEmpty() || path.length()>180 || !path.matches("[a-zA-Z0-9_./ -]+") || path.startsWith("/"))return false;for(String part:path.split("/"))if(part.equals("..") || part.equals("."))return false;return true;}
 public static ThemePack read(File folder)throws Exception{return new ThemePack(folder,manifest(folder));}
 private static JSONObject manifest(File folder)throws Exception{File manifest=new File(folder,"manifest.json");if(manifest.length()>65536)throw new IOException("manifest.json 过大");try(InputStream in=new FileInputStream(manifest)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>65536)throw new IOException("manifest.json 过大");}return new JSONObject(out.toString("UTF-8"));}}
 public static ThemePack install(InputStream input,File root)throws Exception{
  if(!root.isDirectory() && !root.mkdirs())throw new IOException("无法创建扩展目录");File stage=new File(root,"pack-"+UUID.randomUUID().toString());if(!stage.mkdir())throw new IOException("无法创建临时目录");boolean success=false;
  try(ZipInputStream zip=new ZipInputStream(input)){Set<String> seen=new HashSet<>();int entries=0;long total=0;ZipEntry entry;byte[] buf=new byte[16384];while((entry=zip.getNextEntry())!=null){String path=entry.getName();if(++entries>96 || !safePath(path) || !seen.add(path))throw new IOException("扩展包含重复或无效路径");File f=new File(stage,path);if(!f.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("无效路径");if(entry.isDirectory()){f.mkdirs();continue;}if(!path.toLowerCase(Locale.ROOT).matches(".*\\.(json|png|jpg|jpeg|webp)"))throw new IOException("静态主题仅支持 JSON、PNG、JPEG、WebP");File parent=f.getParentFile();if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("无法写入资源");try(OutputStream out=new FileOutputStream(f)){long size=0;int n;while((n=zip.read(buf))!=-1){size+=n;total+=n;if(size>10*1024*1024 || total>24*1024*1024)throw new IOException("扩展过大，最多 24 MB");out.write(buf,0,n);}}}
   ThemePack pack=read(stage);success=true;return pack;
  }finally{if(!success)delete(stage);}
 }
 static void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
 public static File root(Context c){return new File(c.getFilesDir(),"extensions");}
 public static ThemePack active(Context c){String name=c.getSharedPreferences("extensions",0).getString("active","");if(!name.matches("pack-[a-zA-Z0-9-]+"))return null;try{return read(new File(root(c),name));}catch(Exception e){select(c,null);return null;}}
 /** Remove only retired script packs; preserve static themes and all music data. */
 public static void removeLegacyPlugins(Context c){
  File[] folders=root(c).listFiles();if(folders!=null)for(File dir:folders)try{
   if(!dir.getName().matches("pack-[a-zA-Z0-9-]+") || !root(c).getCanonicalFile().equals(dir.getCanonicalFile().getParentFile()))continue;
   JSONObject m=manifest(dir);if("gmusic.plugin.v1".equals(m.optString("format")) || m.has("script")){
    if(dir.getName().equals(c.getSharedPreferences("extensions",0).getString("active","")))select(c,null);
    delete(dir);
   }
  }catch(Exception ignored){}
  delete(new File(c.getCacheDir(),"theme-preview"));
 }
 public static void select(Context c,ThemePack p){c.getSharedPreferences("extensions",0).edit().putString("active",p==null?"":p.directory.getName()).apply();}
 public static void remove(Context c,ThemePack pack)throws IOException{
  File folder=pack.directory.getCanonicalFile(),base=root(c).getCanonicalFile();if(!base.equals(folder.getParentFile()) || !folder.getName().matches("pack-[a-zA-Z0-9-]+"))throw new IOException("无效的主题目录");
  String active=c.getSharedPreferences("extensions",0).getString("active","");if(folder.getName().equals(active))select(c,null);delete(folder);if(folder.exists())throw new IOException("主题文件未能全部删除，请重试");
 }
 public static ArrayList<ThemePack> list(Context c){ArrayList<ThemePack> list=new ArrayList<>();File[] folders=root(c).listFiles();if(folders!=null)for(File dir:folders)try{list.add(read(dir));}catch(Exception ignored){}list.sort((a,b)->a.name.compareTo(b.name));return list;}
 public Drawable background()throws Exception{
  if(wallpaper.isEmpty())return null;File file=asset(wallpaper);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap==null)throw new IOException("壁纸无法读取");
  return new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);public void draw(Canvas c){Rect b=getBounds();float scale=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;int checkpoint=c.save();c.clipRect(b);c.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),paint);c.drawColor(0xa6000000);c.restoreToCount(checkpoint);}public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.OPAQUE;}};
 }
}
