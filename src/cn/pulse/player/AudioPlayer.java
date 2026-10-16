package cn.pulse.player;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import java.util.Map;
/** Small platform boundary so delayed prepare/seek callbacks can be checked deterministically. */
interface AudioPlayer {
 interface Listener {void prepared();void completed();void seekCompleted();void failed();}
 void source(Context context,String uri,Map<String,String> headers)throws Exception;
 void prepare();void start();void pause();void seek(int position);void volume(float value);void release();
 boolean playing();int position();int duration();int session();
 final class Native implements AudioPlayer {
  private final MediaPlayer media=new MediaPlayer();
  Native(Context context,Listener listener){
   media.setWakeMode(context.getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
   media.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
   media.setOnPreparedListener(p->listener.prepared());media.setOnCompletionListener(p->listener.completed());
   media.setOnSeekCompleteListener(p->listener.seekCompleted());media.setOnErrorListener((p,w,e)->{listener.failed();return true;});
  }
  public void source(Context context,String uri,Map<String,String> headers)throws Exception{media.setDataSource(context,Uri.parse(uri),headers);}
  public void prepare(){media.prepareAsync();}public void start(){media.start();}public void pause(){media.pause();}
  public void seek(int position){media.seekTo(position,MediaPlayer.SEEK_CLOSEST);}
  public void volume(float value){media.setVolume(value,value);}public void release(){media.release();}
  public boolean playing(){return media.isPlaying();}public int position(){return media.getCurrentPosition();}
  public int duration(){return media.getDuration();}public int session(){return media.getAudioSessionId();}
 }
}
