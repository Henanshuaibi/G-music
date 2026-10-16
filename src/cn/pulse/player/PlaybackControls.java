package cn.pulse.player;
import android.media.session.PlaybackState;
import android.os.Bundle;
/** Same custom-action order on Android 13+ as on the MediaStyle notification. */
public final class PlaybackControls {
 private PlaybackControls(){}
 public static int modeIcon(int mode){return mode==PlaybackOrder.ONE?R.drawable.repeat_one:mode==PlaybackOrder.SHUFFLE?R.drawable.shuffle:R.drawable.repeat_all;}
 public static void addCustomActions(PlaybackState.Builder state,String source,boolean liked,int mode){
  if(source!=null){Bundle extras=new Bundle();extras.putString("source",source);state.addCustomAction(new PlaybackState.CustomAction.Builder("favorite",liked?"取消喜欢":"喜欢",liked?R.drawable.heart_filled:R.drawable.heart_outline).setExtras(extras).build());}
  state.addCustomAction(new PlaybackState.CustomAction.Builder("playback_mode",PlaybackOrder.label(mode),modeIcon(mode)).build());
 }
}
