package cn.pulse.player;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
public final class BiliClient {
 public static final class Result {public final ArrayList<PlayerService.Track> parts=new ArrayList<>();public int selected;public String media,mime="video/mp4";public int expectedMs;}
 private static JSONObject request(String address)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Referer","https://www.bilibili.com/");
  try{if(c.getResponseCode()!=200)throw new IOException("B站暂时限制请求，请稍后重试");StringBuilder text=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;while((line=r.readLine())!=null){text.append(line);if(text.length()>3000000)throw new IOException("B站响应过大");}}JSONObject result=new JSONObject(text.toString());if(result.optInt("code",-1)!=0)throw new IOException("视频当前无法公开播放，请换一个结果");return result.getJSONObject("data");}finally{c.disconnect();}
 }
 public static String shortLink(String link)throws Exception{
  String location=link;
  for(int redirects=0;redirects<5;redirects++){URL url=new URL(location);String host=url.getHost();if(!"https".equals(url.getProtocol()) || !("b23.tv".equalsIgnoreCase(host)||"bilibili.com".equalsIgnoreCase(host)||host.toLowerCase(Locale.ROOT).endsWith(".bilibili.com")))throw new IOException("无效的 B站分享链接");
   if(!"b23.tv".equalsIgnoreCase(host)){java.util.regex.Matcher bv=java.util.regex.Pattern.compile("BV[0-9A-Za-z]{10}").matcher(url.getPath());if(bv.find()){String page=android.net.Uri.parse(location).getQueryParameter("p");return bv.group()+(page==null?"":"?p="+page);}}
   HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Mozilla/5.0");try{int code=c.getResponseCode();if(code>=300 && code<400){String next=c.getHeaderField("Location");if(next==null)break;location=new URL(url,next).toString();}else break;}finally{c.disconnect();}
  }throw new IOException("无法解析分享链接，请粘贴完整 BV视频地址");
 }
 public static Result open(String id)throws Exception{
  String bv=id.split("\\?",2)[0];if(!bv.matches("BV[0-9A-Za-z]{10}"))throw new IOException("无效的 B站视频编号");
  android.net.Uri input=android.net.Uri.parse("https://www.bilibili.com/video/"+id);String wantedCid=input.getQueryParameter("cid"),wantedPage=input.getQueryParameter("p");
  JSONObject info=request("https://api.bilibili.com/x/web-interface/view?bvid="+bv);JSONArray pages=info.optJSONArray("pages");
  if(pages==null || pages.length()==0){pages=new JSONArray().put(new JSONObject().put("cid",info.getLong("cid")).put("part",info.optString("title")).put("duration",info.optInt("duration")));}
  Result result=new Result();String author=info.optJSONObject("owner")==null?"B站":info.getJSONObject("owner").optString("name","B站");boolean found=wantedCid==null;
  int page=0;if(wantedPage!=null)try{page=Integer.parseInt(wantedPage)-1;}catch(Exception ignored){}if(page<0 || page>=pages.length())throw new IOException("视频分集不存在");
  for(int i=0;i<pages.length();i++){JSONObject part=pages.getJSONObject(i);long cid=part.getLong("cid");if(wantedCid!=null && !wantedCid.equals(Long.toString(cid)))continue;found=true;
   String title=pages.length()>1?part.optString("part",info.optString("title")):info.optString("title","B站视频");
   result.parts.add(new PlayerService.Track(title,"B站 · "+author+(pages.length()>1?" · 分集 "+(i+1)+"/"+pages.length():""),"bili:"+bv+"?cid="+cid,part.optInt("duration")*1000));
  }
  if(!found)throw new IOException("视频分集已失效");result.selected=wantedCid==null?page:0;
  PlayerService.Track selected=result.parts.get(result.selected);String cid=selected.url.substring(selected.url.indexOf("?cid=")+5);result.expectedMs=selected.expectedMs;
  JSONObject media=request("https://api.bilibili.com/x/player/playurl?bvid="+bv+"&cid="+cid+"&qn=16&fnval=16&fourk=0");
  if(media.optInt("is_preview",0)!=0)throw new IOException("该视频仅提供试看，请选择公开视频");
  if(result.expectedMs>0 && media.optLong("timelength")>0 && media.optLong("timelength")+5000<result.expectedMs)throw new IOException("音源时长与分集不符，已停止加载，请换一个结果");
  JSONObject dash=media.optJSONObject("dash");JSONArray audio=dash==null?null:dash.optJSONArray("audio");
  if(audio!=null && audio.length()>0){JSONObject best=audio.getJSONObject(0);for(int i=1;i<audio.length();i++){JSONObject item=audio.getJSONObject(i);if(item.optInt("bandwidth")>best.optInt("bandwidth"))best=item;}String u=best.optString("baseUrl",best.optString("base_url"));if(u.startsWith("https://")){result.media=u;result.mime=best.optString("mimeType",best.optString("mime_type","audio/mp4"));}}
  JSONArray direct=media.optJSONArray("durl");if(result.media==null && direct!=null && direct.length()==1){String u=direct.getJSONObject(0).optString("url");if(u.startsWith("https://"))result.media=u;}
  if(result.media==null)throw new IOException("该视频没有可播放的公开音频，请换一个结果");return result;
 }
}
