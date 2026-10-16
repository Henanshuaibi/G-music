package cn.pulse.player;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.*;

public final class TrackDownloads {
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final Set<String> resolving=Collections.synchronizedSet(new HashSet<>());
 public static void start(Context context,PlayerService.Track track){
  Context app=context.getApplicationContext();
  if(!track.url.startsWith("https://") && !track.url.startsWith("bili:") && !track.url.startsWith("bili-link:")){say(app,"本地歌曲已可离线播放");return;}
  if(!resolving.add(track.url)){say(app,"正在准备这首歌的下载");return;}say(app,"正在准备下载…");
  worker.execute(()->{try{
   DownloadManager manager=(DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE);
   long old=app.getSharedPreferences("downloads",0).getLong(track.url,-1);
   if(old!=-1)try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(old))){if(c!=null && c.moveToFirst()){int state=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));if(state!=DownloadManager.STATUS_FAILED){say(app,state==DownloadManager.STATUS_SUCCESSFUL?"已下载，保存位置：Download/G music":"这首歌已在下载队列中");return;}}}
   String source=track.url,name=track.title,mime=null,extension=null;boolean bili=source.startsWith("bili:") || source.startsWith("bili-link:");
   if(bili){BiliClient.Result result=BiliClient.open(source.startsWith("bili:")?source.substring(5):BiliClient.shortLink(source.substring(10)));if(result.parts.size()!=1)throw new java.io.IOException("请先播放并展开合集，再下载其中的单曲");source=result.media;name=result.parts.get(result.selected).title;mime=result.mime;if(!mime.startsWith("audio/"))throw new java.io.IOException("此资源未提供独立音轨，请选择其他版本");extension=mime.contains("webm")?"webm":"m4a";}
   if(extension==null){String path=Uri.parse(source).getLastPathSegment();java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)\\.(mp3|m4a|aac|flac|ogg|opus|wav|webm|mp4)$").matcher(path==null?"":path);extension=m.find()?m.group(1).toLowerCase(Locale.ROOT):"m4a";}
   String safe=name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(safe.length()>70)safe=safe.substring(0,70);if(safe.isEmpty())safe="歌曲";
   DownloadManager.Request request=new DownloadManager.Request(Uri.parse(source)).setTitle(name).setDescription("G music · 歌曲下载").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"G music/"+safe+"-"+System.currentTimeMillis()+"."+extension);
   if(mime!=null)request.setMimeType(mime);request.addRequestHeader("User-Agent","Mozilla/5.0");if(bili)request.addRequestHeader("Referer","https://www.bilibili.com/");
   long id=manager.enqueue(request);app.getSharedPreferences("downloads",0).edit().putLong(track.url,id).apply();say(app,"已开始下载，保存到 Download/G music");
  }catch(Exception ex){say(app,"下载未开始："+(ex.getMessage()==null?"请检查网络后重试":ex.getMessage()));}finally{resolving.remove(track.url);}});
 }
 private static void say(Context c,String text){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c,text,Toast.LENGTH_LONG).show());}
}
