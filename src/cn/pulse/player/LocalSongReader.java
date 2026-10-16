package cn.pulse.player;
import android.content.*;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
final class LocalSongReader implements SongImportTask.Reader {
 private final Context context;
 LocalSongReader(Context context){this.context=context.getApplicationContext();}
 public PlayerService.Track read(String source)throws Exception{
  Uri uri=Uri.parse(source);ContentResolver resolver=context.getContentResolver();
  try{resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
  String name="本地歌曲",artist="本地音乐";int duration=0;
  try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null && c.moveToFirst() && !c.isNull(0))name=c.getString(0);}catch(Exception ignored){}
  // Skip unreadable documents, but keep playable files even when optional tags are missing.
  try(ParcelFileDescriptor file=resolver.openFileDescriptor(uri,"r")){
   if(file==null)throw new java.io.IOException("无法读取音频");
   MediaMetadataRetriever metadata=new MediaMetadataRetriever();
   try{metadata.setDataSource(context,uri);String title=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),singer=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),length=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(title!=null && !title.trim().isEmpty())name=title;if(singer!=null && !singer.trim().isEmpty())artist=singer;if(length!=null)duration=Math.max(0,Integer.parseInt(length));}catch(Exception ignored){}finally{try{metadata.release();}catch(Exception ignored){}}
  }
  return new PlayerService.Track(name,artist,source,duration);
 }
}
