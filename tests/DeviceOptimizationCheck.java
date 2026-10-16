package cn.pulse.player;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Real service/Looper/decoder checks; the audio output engine is controlled, with no UI or speaker use. */
public final class DeviceOptimizationCheck {
 static Handler main;
 static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 static Object field(Object target,Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void field(Object target,Class<?> owner,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static <T>T ui(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);main.post(task);return task.get(8,TimeUnit.SECONDS);}
 static final class Player extends PlayerService {
  DevicePlaybackRecoveryCheck.Engine engine;int creations;
  final DevicePlaybackRecoveryCheck.Store store;
  Player()throws Exception{store=new DevicePlaybackRecoveryCheck.Store();attachBaseContext(store);SharedPreferences prefs=store.getSharedPreferences("favorites",0);field(this,PlayerService.class,"favorites",new FavoriteStore(new FavoriteStore.Storage(){public String read(){return prefs.getString("items","[]");}public void write(String value){prefs.edit().putString("items",value).apply();}}));}
  public Context getApplicationContext(){return this;}
  AudioPlayer createPlayer(AudioPlayer.Listener listener){creations++;return engine=new DevicePlaybackRecoveryCheck.Engine(listener);}
  void ensureForeground(){}boolean requestFocus(){return true;}void stopPlaybackService(){}
 }
 static PcmAnalyzer analyzer(Player p)throws Exception{return (PcmAnalyzer)field(p,PlayerService.class,"analyzer");}
 static boolean closed(PcmAnalyzer a)throws Exception{return (Boolean)field(a,PcmAnalyzer.class,"closed");}
 static boolean pending(Player p,String task)throws Exception{return ((Handler)field(p,PlayerService.class,"main")).hasCallbacks((Runnable)field(p,PlayerService.class,task));}
 static void awaitFrame(Player p)throws Exception{AudioReaction signal=new AudioReaction();long until=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<until){if(ui(()->p.analysisFrame(signal,SystemClock.uptimeMillis())))return;Thread.sleep(20);}throw new AssertionError("decoder did not become ready: "+ui(p::analysisStatus));}
 static void assertSettings(Context c,int circle,int motion,int bars,String why){VisualSettings v=VisualSettings.load(c);check(v.circle==circle && v.motion==motion && v.bars==bars,why);}
 public static void main(String[] args){Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());new Thread(()->{try{lifetime(args[0]);ui(()->{backups();return null;});System.out.println("PASS: shared native decoder survives repeated surface handoffs and late detach; idle decoder closes; state notifications wake paused playback; no paused checkpoint; visual backup/legacy restore/validation preserve playback");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}},"check-026").start();Looper.loop();}
 static void lifetime(String path)throws Exception{
  Player p=ui(()->new Player());Object portrait=new Object(),landscape=new Object();int[] events={0};Runnable listener=()->events[0]++;
  ui(()->{p.add(Collections.singletonList(new PlayerService.Track("tone","test",path,18000)));p.addListener(listener);p.addListener(listener);p.play(0);check(events[0]==1 && p.playRequested(),"loading dispatches once despite repeated subscription");p.engine.prepared();check(events[0]==2 && p.playing(),"prepared playback immediately notifies surfaces");check(pending(p,"checkpoint"),"playing keeps the progress checkpoint");p.retainAnalysis(portrait);return null;});
  awaitFrame(p);PcmAnalyzer warmed=ui(()->analyzer(p));
  ui(()->{p.releaseAnalysis(portrait);check(pending(p,"releaseIdleAnalysis") && analyzer(p)==warmed,"departing surface keeps its warmed decoder briefly");return null;});Thread.sleep(200);
  ui(()->{p.retainAnalysis(landscape);check(analyzer(p)==warmed && !pending(p,"releaseIdleAnalysis"),"new surface cancels release and reuses the decoder");p.releaseAnalysis(portrait);check(!pending(p,"releaseIdleAnalysis"),"late duplicate detach cannot release a different surface");check(p.analysisFrame(new AudioReaction(),SystemClock.uptimeMillis()),"new surface receives a frame immediately without rebuffering");
   for(int i=0;i<12;i++){p.retainAnalysis(portrait);p.releaseAnalysis(landscape);p.releaseAnalysis(landscape);p.retainAnalysis(landscape);p.releaseAnalysis(portrait);check(analyzer(p)==warmed && !closed(warmed) && !pending(p,"releaseIdleAnalysis"),"repeated and overlapping handoffs preserve the same decoder");}
   p.pause();p.releaseAnalysis(landscape);check(!pending(p,"checkpoint"),"pause stops periodic disk checkpoints");return null;});
  int paused=ui(()->events[0]);Thread.sleep(1800);
  ui(()->{check(events[0]==paused && !pending(p,"checkpoint"),"paused state does not poll or repeatedly publish updates");check(analyzer(p)==null && closed(warmed),"idle analysis closes after grace period");check(!p.analysisFrame(new AudioReaction(),SystemClock.uptimeMillis()) && analyzer(p)==null,"unowned surface cannot recreate analysis");
   // Notification toggle must wake observers even after all periodic UI polling has stopped.
   p.onStartCommand(new Intent().setAction("toggle"),0,0);check(events[0]>paused && p.playing(),"notification resume delivers state without a polling timer");p.retainAnalysis(portrait);return null;});
  awaitFrame(p);PcmAnalyzer replacement=ui(()->analyzer(p));check(replacement!=warmed,"resume after idle creates a fresh decoder");
  ui(()->{p.play(0);check(closed(replacement),"changing song closes analysis immediately despite surface ownership");p.engine.prepared();return null;});awaitFrame(p);
  ui(()->{int before=events[0];p.cyclePlaybackMode();p.toggleFavorite(p.current());check(events[0]==before+2,"mode and favorite actions publish changes while listening");p.pause();p.seek(700);p.engine.seekDone();check(p.position()==700 && !p.playing(),"paused seek completes without a periodic refresh or autoplay");
   p.removeListener(listener);int detached=events[0];p.resume();check(events[0]==detached,"hidden activity no longer receives service updates");p.pause();p.releaseAnalysis(portrait);p.clear();check(analyzer(p)==null && !pending(p,"releaseIdleAnalysis") && !pending(p,"checkpoint"),"clear cancels both analysis cleanup and checkpoint timers");return null;});
  Thread worker=(Thread)field(warmed,PcmAnalyzer.class,"worker");worker.join(2000);check(!worker.isAlive(),"idle decoder worker terminates");
 }
 static JSONObject song(String name,String url)throws Exception{return new JSONObject().put("title",name).put("artist","歌手").put("url",url).put("duration",180000);}
 static void backups()throws Exception{
  Player p=new Player();p.add(Collections.singletonList(new PlayerService.Track("current","artist","https://example.test/current",180000)));p.play(0);p.engine.prepared();p.engine.pos=43210;p.toggleFavorite(p.current());new VisualSettings(84,123,157).save(p);
  LibraryBackup.Data captured=LibraryBackup.parse(LibraryBackup.capture(p));check(captured.visuals.circle==84 && captured.visuals.motion==123 && captured.visuals.bars==157,"export captures all saved visual settings");check(captured.queue.size()==1 && captured.favorites.size()==1,"export retains library data");
  JSONArray queue=new JSONArray().put(song("新增歌曲","https://example.test/new")),favorites=new JSONArray().put(song("新增喜欢","https://example.test/liked"));
  String legacy=LibraryBackup.encode(queue,favorites,2);LibraryBackup.Data old=LibraryBackup.parse(legacy);check(old.visuals==null,"missing visual block means preserve existing settings");p.restoreLibrary(old);assertSettings(p,84,123,157,"restoring old backup does not reset custom visuals");
  p.getSharedPreferences("visuals",0).edit().putString("future","keep").apply();String modern=LibraryBackup.encode(queue,favorites,1,new VisualSettings(77,0,180));LibraryBackup.Data restored=LibraryBackup.read(new ByteArrayInputStream(modern.getBytes("UTF-8")));int[] added=p.restoreLibrary(restored);assertSettings(new ContextWrapper(p.store),77,0,180,"restored settings survive a new context");check(p.getSharedPreferences("visuals",0).getString("future","").equals("keep"),"visual restore preserves unrelated preferences");
  check(added[0]==0 && added[1]==0 && p.tracks.size()==2 && p.favorites().size()==2,"repeat merge preserves existing queue and favorites without duplicates");check(p.engine.starts==1 && p.creations==1 && p.engine.pos==43210 && p.playing(),"restoring visuals never seeks or restarts music");check(p.playbackMode()==1,"restored mode is applied");
  LibraryBackup.Data roundtrip=LibraryBackup.parse(LibraryBackup.capture(p));check(roundtrip.visuals.circle==77 && roundtrip.visuals.motion==0 && roundtrip.visuals.bars==180,"restored zero and maximum values export exactly");
  for(Object bad:new Object[]{JSONObject.NULL,new JSONArray(),"broken",new JSONObject().put("circle",100).put("motion",100),new JSONObject().put("circle",100).put("motion","0").put("bars",100),new JSONObject().put("circle",100).put("motion",2.5).put("bars",100),new JSONObject().put("circle",116).put("motion",100).put("bars",100),new JSONObject().put("circle",100).put("motion",-1).put("bars",100),new JSONObject().put("circle",100).put("motion",100).put("bars",181)}){
   boolean rejected=false;try{LibraryBackup.parse(new JSONObject(modern).put("visuals",bad).toString());}catch(Exception expected){rejected=true;}check(rejected,"malformed visual settings rejected before any restore");assertSettings(p,77,0,180,"invalid backup leaves preferences unchanged");
  }
  p.restoreLibrary(LibraryBackup.parse(LibraryBackup.encode(queue,favorites,0,new VisualSettings(115,200,50))));assertSettings(p,115,200,50,"all supported bounds restore exactly");p.pause();p.clear();
 }
}
