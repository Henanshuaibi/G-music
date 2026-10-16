package cn.pulse.player;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;

/** Portable static JSON themes with optional embedded images. */
public final class ThemeText {
 public static final int MAX_BYTES=512*1024;
 private ThemeText(){}
 public static String example(){return "{\n  \"format\": \"gmusic.theme.v1\",\n  \"name\": \"月白\",\n  \"background\": \"#080808\",\n  \"accent\": \"#E8E8E8\"\n}";}
 private static void limit(String text)throws IOException{if(text==null || text.length()>MAX_BYTES || text.getBytes("UTF-8").length>MAX_BYTES)throw new IOException("主题代码最多 512 KB，请精简代码或压缩内嵌图片");}
 public static String readText(InputStream in,int max)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("文本超过大小限制");out.write(buf,0,n);}
  try{String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();return value.startsWith("\ufeff")?value.substring(1):value;}catch(CharacterCodingException e){throw new IOException("请使用 UTF-8 编码保存文件");}
 }
 public static ThemePack install(String text,File root)throws Exception{
  limit(text);text=text.trim();if(text.startsWith("```")){int start=text.indexOf('\n');if(start<0 || !text.endsWith("```"))throw new IOException("请粘贴完整的主题配置");text=text.substring(start+1,text.length()-3).trim();}
  if(!text.startsWith("{") || !text.endsWith("}"))throw new IOException("请使用完整的静态主题 JSON 配置");
  JSONObject manifest;try{manifest=new JSONObject(text);}catch(JSONException e){throw new IOException("主题代码格式有误，请检查引号、逗号和括号");}
  JSONObject assets=manifest.optJSONObject("assets");if(manifest.has("assets") && assets==null)throw new IOException("assets 必须为资源名称与内容组成的对象");manifest.remove("assets");return installManifest(manifest,assets,root);
 }
 private static ThemePack installManifest(JSONObject manifest,JSONObject assets,File root)throws Exception{
  if(!"gmusic.theme.v1".equals(manifest.optString("format")) || manifest.has("script"))throw new IOException("仅支持静态主题 gmusic.theme.v1");manifest.remove("pet");
  if(!root.isDirectory() && !root.mkdirs())throw new IOException("无法创建主题目录");File stage=new File(root,"pack-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("无法创建临时主题");boolean success=false;
  try{
   if(assets!=null){if(assets.length()>95)throw new IOException("资源过多");Iterator<String> keys=assets.keys();while(keys.hasNext()){
    String path=keys.next();if(!ThemePack.safePath(path) || !path.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)"))throw new IOException("资源仅支持 PNG、JPEG、WebP 图片");
    Object value=assets.get(path);if(!(value instanceof String))throw new IOException("资源内容需要文本");String encoded=(String)value;byte[] bytes;
     if(encoded.startsWith("data:")){int comma=encoded.indexOf(',');if(comma<0 || !encoded.substring(0,comma).matches("data:image/(png|jpeg|webp);base64"))throw new IOException("图片需使用 PNG、JPEG 或 WebP 的 Base64 数据");encoded=encoded.substring(comma+1);}
     try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IOException("内嵌图片编码不正确");}
    File file=new File(stage,path);if(!file.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("无效资源路径");File parent=file.getParentFile();if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("无法保存资源");try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
   }}
   byte[] config=manifest.toString(2).getBytes("UTF-8");if(config.length>65536)throw new IOException("主题配置过长");try(OutputStream out=new FileOutputStream(new File(stage,"manifest.json"))){out.write(config);}
   ThemePack pack=ThemePack.read(stage);success=true;return pack;
  }finally{if(!success)ThemePack.delete(stage);}
 }
 public static ThemePack save(ThemePack staged,File root)throws Exception{
  if(!root.isDirectory() && !root.mkdirs())throw new IOException("无法保存主题");File target=new File(root,staged.directory.getName());if(target.exists() || !staged.directory.renameTo(target))throw new IOException("主题保存失败，请重试");
  try{return ThemePack.read(target);}catch(Exception e){target.renameTo(staged.directory);throw e;}
 }
 public static String encode(ThemePack pack)throws Exception{
  if(pack==null)return new JSONObject().put("format","gmusic.theme.v1").put("name","黑白极简").put("background","#080808").put("accent","#EEEEEE").toString(2);
  JSONObject manifest=new JSONObject(new String(readBytes(new File(pack.directory,"manifest.json"),65536),"UTF-8"));manifest.remove("assets");manifest.remove("pet");JSONObject assets=new JSONObject();Set<String> paths=new LinkedHashSet<>();if(!pack.wallpaper.isEmpty())paths.add(pack.wallpaper);
  int size=0;for(String path:paths){byte[] bytes=readBytes(pack.asset(path),MAX_BYTES);size+=bytes.length;if(size>MAX_BYTES)throw new IOException("主题资源过大，无法复制为主题代码");String suffix=path.toLowerCase(Locale.ROOT);
   {String mime=suffix.endsWith(".png")?"png":suffix.endsWith(".webp")?"webp":"jpeg";assets.put(path,"data:image/"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes));}
  }
  if(assets.length()>0)manifest.put("assets",assets);String result=manifest.toString(2);limit(result);return result;
 }
 private static byte[] readBytes(File file,int max)throws IOException{
  try(InputStream in=new FileInputStream(file)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("主题内容过大，无法复制为主题代码");out.write(buf,0,n);}return out.toByteArray();}
 }
}
