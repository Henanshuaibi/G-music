package cn.pulse.player;
import android.content.*;
import android.media.AudioManager;
import android.os.Looper;
import java.util.*;
import java.lang.reflect.*;
import org.json.*;
public final class DevicePlaybackRecoveryCheck {
 static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 static final class Prefs implements InvocationHandler {
  final Map<String,Object> values=new HashMap<>(),pending=new HashMap<>();final SharedPreferences prefs;final SharedPreferences.Editor editor;
  Prefs(){prefs=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},this);editor=(SharedPreferences.Editor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},this);}
  public Object invoke(Object proxy,Method method,Object[] args){String name=method.getName();if(name.equals("edit"))return editor;if(name.startsWith("put")){pending.put((String)args[0],args[1]);return editor;}if(name.equals("apply") || name.equals("commit")){values.putAll(pending);pending.clear();return name.equals("commit")?true:null;}if(name.startsWith("get") && args!=null && args.length==2){Object value=values.getOrDefault(args[0],args[1]);if(name.equals("getInt") && !(value instanceof Integer))throw new ClassCastException("expected integer preference");return value;}return null;}
 }
 static final class Store extends ContextWrapper {
  final Map<String,Prefs> stores=new HashMap<>();Store(){super(null);}
  public SharedPreferences getSharedPreferences(String name,int mode){if(!stores.containsKey(name))stores.put(name,new Prefs());return stores.get(name).prefs;}
 }
 static final class Engine implements AudioPlayer {
  final Listener listener;int starts,pauses,seeks,pos,target,len=180000;boolean prepared,active,released;
  Engine(Listener listener){this.listener=listener;}
  public void source(Context c,String u,Map<String,String> h){}public void prepare(){}
  void prepared(){prepared=true;listener.prepared();}void seekDone(){pos=target;listener.seekCompleted();}
  public void start(){check(prepared && !released,"only start a prepared live engine");starts++;active=true;}
  public void pause(){check(prepared && !released,"only pause a prepared live engine");pauses++;active=false;}
  public void seek(int position){check(prepared && !released,"only seek a prepared live engine");target=position;seeks++;}
  public void volume(float value){}public void release(){released=true;active=false;}
  public boolean playing(){return active;}public int position(){return pos;}public int duration(){return len;}public int session(){return 1;}
 }
 static final class Player extends PlayerService {
  Engine engine;int creations;boolean focusGranted=true;
  Player(Store store){attachBaseContext(store);}
  AudioPlayer createPlayer(AudioPlayer.Listener l){creations++;return engine=new Engine(l);}
  void ensureForeground(){}boolean requestFocus(){return focusGranted;}void stopPlaybackService(){}void update(){}
 }
 static PlayerService.Track track(String title,String url){return new PlayerService.Track(title,"artist",url,180000);}
 static Player player(Store store){Player p=new Player(store);p.add(Arrays.asList(track("first","content://audio/same"),track("second copy","content://audio/same"),track("third","content://audio/other")));return p;}
 public static void main(String[] args){try{Looper.prepareMainLooper();run();System.out.println("PASS: production service delayed prepare/pause/noisy/focus/seek, stale callbacks, duplicate selection, restart/bookmark/reorder/removal/clear/legacy recovery");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run()throws Exception{
  Store store=new Store();Player p=player(store);p.play(1);Engine first=p.engine;check(p.index==1 && p.playRequested(),"exact selected index starts loading");p.toggle();first.prepared();check(first.starts==0 && !p.playRequested() && !p.playing(),"pause while preparing prevents later auto-start");p.toggle();check(p.creations==1 && first.starts==1,"resume reuses paused prepared engine");p.pause();check(!first.active,"normal pause still works");
  p.play(0);Engine noisy=p.engine;Field field=PlayerService.class.getDeclaredField("noisy");field.setAccessible(true);((BroadcastReceiver)field.get(p)).onReceive(store,new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));noisy.prepared();check(noisy.starts==0 && !p.playRequested(),"unplug headphones while loading prevents playback");
  p.play(0);Engine repeated=p.engine;p.toggle();p.toggle();repeated.prepared();check(repeated.starts==1 && p.creations==3,"pause then resume during prepare uses the same load");
  p.play(1);Engine stale=p.engine;p.play(2);Engine latest=p.engine;stale.prepared();stale.listener.failed();stale.listener.completed();check(latest.starts==0 && p.index==2 && p.playRequested(),"old player callbacks cannot change current selection");latest.prepared();check(latest.starts==1,"new player still starts normally");
  p.play(0);Engine focus=p.engine;p.audioFocusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);focus.prepared();check(focus.starts==0,"transient focus loss holds prepared playback");p.pause();p.audioFocusChanged(AudioManager.AUDIOFOCUS_GAIN);check(focus.starts==0,"manual pause cancels automatic focus resume");
  p.resume();p.audioFocusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);p.audioFocusChanged(AudioManager.AUDIOFOCUS_GAIN);check(focus.starts==2 && p.playing(),"temporary interruption resumes only when not manually cancelled");
  p.play(0);Engine focusLoad=p.engine;p.audioFocusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);p.audioFocusChanged(AudioManager.AUDIOFOCUS_GAIN);focusLoad.prepared();check(focusLoad.starts==1,"focus gain before prepare restores pending intent");
  p.play(0);Engine denied=p.engine;p.focusGranted=false;denied.prepared();check(denied.starts==0 && !p.playRequested(),"focus denial does not show pending playback forever");p.focusGranted=true;p.resume();check(denied.starts==1,"explicit retry after focus denial works");
  p.playTrack(p.tracks.get(1));check(p.index==1,"same URL second occurrence plays exactly clicked row");p.engine.prepared();p.engine.pos=76123;p.pause();
  Player restored=new Player(store);restored.restoreQueue();check(restored.tracks.size()==3 && restored.index==1 && restored.current().title.equals("second copy"),"restart restores exact duplicate occurrence");check(restored.position()==76123 && restored.duration()==180000 && !restored.playRequested() && restored.creations==0,"restart retains position and stays paused without network or player creation");
  restored.resume();Engine resume=restored.engine;resume.prepared();check(resume.starts==0 && resume.target==76123 && restored.position()==76123,"restore seeks before making sound");restored.pause();resume.seekDone();check(resume.starts==0 && !restored.playRequested(),"pause while restoring seek remains paused");restored.resume();check(resume.starts==1 && resume.pos==76123,"resume begins at restored position");
  restored.seek(40000);restored.seek(90000);check(restored.position()==90000 && resume.seeks==2,"new seek updates displayed target but serializes native calls");resume.seekDone();check(resume.target==90000 && resume.seeks==3,"pending latest seek runs after previous completes");resume.seekDone();check(restored.position()==90000 && resume.starts==1,"latest target wins without restarting playing engine");
  PlayerService.Track selected=restored.current();check(restored.moveTrack(selected,restored.tracks.get(2),true),"reorder selected duplicate");Player moved=new Player(store);moved.restoreQueue();check(moved.index==2 && moved.current().title.equals("second copy") && moved.position()==90000,"order and bookmark persist atomically");
  moved.removeTrack(moved.tracks.get(0));Player removed=new Player(store);removed.restoreQueue();check(removed.index==1 && removed.current().title.equals("second copy") && removed.position()==90000,"removing earlier row retains correct bookmark");removed.removeTrack(removed.current());Player replaced=new Player(store);replaced.restoreQueue();check(replaced.current().title.equals("third") && replaced.position()==0 && !replaced.playRequested(),"removing selected paused song selects next at zero without autoplay");
  p.play(0);Engine removedPending=p.engine;p.pause();p.removeTrack(p.current());removedPending.prepared();check(!p.playRequested() && p.engine==removedPending && removedPending.released && !p.playing(),"removal after loading pause does not autoplay next song");
  replaced.clear();Player empty=new Player(store);empty.restoreQueue();check(empty.tracks.isEmpty() && empty.current()==null && empty.position()==0,"clear cannot resurrect queue or bookmark on restart");
  Store old=new Store();old.getSharedPreferences("MainActivity",0).edit().putString("queue","[{\"title\":\"legacy\",\"artist\":\"a\",\"url\":\"content://a/1\",\"duration\":12345}]").apply();Player legacy=new Player(old);legacy.restoreQueue();check(legacy.tracks.size()==1 && legacy.index==-1 && !legacy.playRequested(),"old queue without bookmark remains compatible");
  old.getSharedPreferences("MainActivity",0).edit().putInt("queueIndex",0).putString("queueSource","wrong-source").putInt("queuePosition",99999).apply();Player invalid=new Player(old);invalid.restoreQueue();check(invalid.index==-1 && invalid.position()==0,"mismatched bookmark never attaches to another song");
  old.getSharedPreferences("MainActivity",0).edit().putString("queueSource","content://a/1").putInt("queueDuration",12345).apply();Player clamped=new Player(old);clamped.restoreQueue();check(clamped.position()==12345,"out of range bookmark clamps to duration");clamped.seek(-3);check(clamped.position()==0 && clamped.creations==0,"seeking restored paused selection stores target without opening source");
  Store checkpointStore=new Store();Player checkpointPlayer=player(checkpointStore);checkpointPlayer.play(2);checkpointPlayer.engine.prepared();checkpointPlayer.engine.pos=42001;Field timer=PlayerService.class.getDeclaredField("checkpoint");timer.setAccessible(true);((Runnable)timer.get(checkpointPlayer)).run();Player afterKill=new Player(checkpointStore);afterKill.restoreQueue();check(afterKill.index==2 && afterKill.position()==42001 && !afterKill.playRequested(),"periodic checkpoint restores progress without a clean shutdown or pause");
  afterKill.seek(88888);Player pendingSeek=new Player(checkpointStore);pendingSeek.restoreQueue();check(pendingSeek.position()==88888 && pendingSeek.creations==0,"paused seek persists immediately even before preparing an engine");pendingSeek.resume();pendingSeek.pause();pendingSeek.engine.prepared();check(pendingSeek.engine.starts==0 && pendingSeek.engine.target==88888,"pause during restore preparation still permits silent positioning");pendingSeek.engine.seekDone();check(!pendingSeek.playRequested() && pendingSeek.engine.starts==0,"silent restore seek cannot undo pause");
  Store failedStore=new Store();Player failed=player(failedStore);failed.play(0);Engine failedEngine=failed.engine;failedEngine.listener.failed();failedEngine.prepared();check(!failed.playRequested() && failedEngine.starts==0 && failedEngine.released,"error invalidates later prepare callback");failed.resume();failed.engine.prepared();check(failed.engine.starts==1 && failed.creations==2,"retry replaces failed engine");
  failed.play(0);Engine cleared=failed.engine;failed.clear();cleared.prepared();cleared.listener.seekCompleted();cleared.listener.completed();check(failed.current()==null && !failed.playing() && !failed.playRequested(),"clear invalidates every pending callback");
 }
}
