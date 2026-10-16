package cn.pulse.player;
import android.media.session.PlaybackState;
import java.util.*;
public final class DevicePlaybackControlsCheck {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args){try{run();System.out.println("PASS: favorite/mode action order, unique mode icons/labels and cycling");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
 static void run()throws Exception{
  int mode=PlaybackOrder.LIST;int[] icons=new int[3];for(int i=0;i<3;i++){check(mode==i,"mode cycle order");PlaybackState.Builder builder=new PlaybackState.Builder();PlaybackControls.addCustomActions(builder,"content://song",true,mode);List<PlaybackState.CustomAction> actions=builder.build().getCustomActions();check(actions.size()==2 && actions.get(0).getAction().equals("favorite") && actions.get(1).getAction().equals("playback_mode"),"mode after favorite on Android 13+");check(actions.get(1).getName().toString().equals(PlaybackOrder.label(mode)),"mode label reflects state");check(actions.get(0).getExtras().getString("source").equals("content://song"),"favorite source preserved");icons[i]=actions.get(1).getIcon();mode=PlaybackOrder.nextMode(mode);}
  check(mode==PlaybackOrder.LIST && icons[0]!=icons[1] && icons[1]!=icons[2] && icons[0]!=icons[2],"wrap to list with unique icons");PlaybackState.Builder empty=new PlaybackState.Builder();PlaybackControls.addCustomActions(empty,null,false,2);check(empty.build().getCustomActions().size()==1,"mode available before track resolves");
 }
}
