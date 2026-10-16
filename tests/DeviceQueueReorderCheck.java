package cn.pulse.player;
import android.content.*;
import android.os.*;
import java.util.*;
import java.lang.reflect.*;
import org.json.*;
public final class DeviceQueueReorderCheck {
 static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
 static PlayerService.Track track(String name,String url){return new PlayerService.Track(name,"artist",url,180000);}
 static class Prefs implements InvocationHandler {
  final Map<String,Object> values=new HashMap<>();int writes;final SharedPreferences prefs;final SharedPreferences.Editor editor;
  Prefs(){prefs=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},this);editor=(SharedPreferences.Editor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},this);}
  public Object invoke(Object proxy,Method method,Object[] args){String name=method.getName();if(name.equals("edit"))return editor;if(name.startsWith("put")){values.put((String)args[0],args[1]);writes++;return editor;}if(name.equals("apply"))return null;if(name.equals("commit"))return true;if(name.startsWith("get") && args!=null && args.length==2)return values.getOrDefault(args[0],args[1]);return null;}
 }
 static class Player extends PlayerService {
  void attach(Context c){attachBaseContext(c);}
  public void play(int n){throw new AssertionError("reorder must not restart playback");}
  public void pause(){throw new AssertionError("reorder must not pause playback");}
  public void releaseAnalysis(){throw new AssertionError("reorder must not interrupt audio analysis");}
 }
 public static void main(String[] args){try{run();System.out.println("PASS: all reorder directions/current indices, duplicates/stale rows, filtered anchors, insertion geometry/edge scroll, live service persistence without playback restart or favorite/mode writes");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run()throws Exception{
  ArrayList<PlayerService.Track> original=new ArrayList<>();for(int i=0;i<5;i++)original.add(track("song"+i,"content://song/"+i));
  for(int from=0;from<5;from++)for(int target=0;target<5;target++)for(boolean after:new boolean[]{false,true})for(int current=-1;current<5;current++){
   ArrayList<PlayerService.Track> queue=new ArrayList<>(original);PlayerService.Track now=current<0?null:queue.get(current),moving=queue.get(from),anchor=queue.get(target);QueueEdits.Movement result=QueueEdits.move(queue,current,moving,anchor,after);check(result!=null && queue.size()==5,"valid drag preserves all songs");check(new HashSet<>(queue).size()==5,"no dropped or duplicated songs");check(now==null?result.currentIndex==-1:queue.get(result.currentIndex)==now,"keeps exactly the same playing row");
   if(moving!=anchor)check(queue.indexOf(moving)==queue.indexOf(anchor)+(after?1:-1),"insert adjacent to requested anchor");else check(queue.equals(original) && !result.changed,"self drop is unchanged");
   ArrayList<PlayerService.Track> remaining=new ArrayList<>(queue);remaining.remove(moving);ArrayList<PlayerService.Track> previous=new ArrayList<>(original);previous.remove(moving);check(remaining.equals(previous),"other songs retain relative order");
   int next=PlaybackOrder.choose(queue.size(),result.currentIndex,PlaybackOrder.LIST,1,false,new Random(0));if(now!=null)check(queue.get(next)==queue.get((result.currentIndex+1)%5),"next follows reordered queue");
  }
  PlayerService.Track first=track("first copy","same"),second=track("second copy","same"),third=track("third","third");ArrayList<PlayerService.Track> copies=new ArrayList<>(Arrays.asList(first,second,third));QueueEdits.Movement r=QueueEdits.move(copies,1,first,third,true);check(copies.equals(Arrays.asList(second,third,first)) && r.currentIndex==0,"same-source copies remain distinct");
  ArrayList<PlayerService.Track> before=new ArrayList<>(copies);check(QueueEdits.move(copies,0,track("stale","same"),third,false)==null && copies.equals(before),"stale same-URL source cannot move another occurrence");check(QueueEdits.move(copies,0,first,track("stale anchor","third"),false)==null && copies.equals(before),"stale anchor rejects without mutation");
  ArrayList<PlayerService.Track> filtered=new ArrayList<>(original);QueueEdits.move(filtered,2,original.get(4),original.get(1),false);check(filtered.equals(Arrays.asList(original.get(0),original.get(4),original.get(1),original.get(2),original.get(3))),"filtered rows move relative to full queue, hidden rows remain ordered");
  int[] tops={-20,50,140},bottoms={50,140,210};check(QueueDropPosition.slot(0,4,12,tops,bottoms)==4,"clipped first row before midpoint");check(QueueDropPosition.slot(15,4,12,tops,bottoms)==5,"midpoint chooses following gap");check(QueueDropPosition.slot(100,4,12,tops,bottoms)==6,"different row heights");check(QueueDropPosition.slot(240,4,12,tops,bottoms)==7,"drop below visible rows");check(QueueDropPosition.slot(240,9,12,tops,bottoms)==12,"append at queue end");check(QueueDropPosition.slot(0,0,0,new int[0],new int[0])==-1,"empty list cannot drop");
  check(QueueDropPosition.scroll(0,300,48,12)==-12 && QueueDropPosition.scroll(300,300,48,12)==12,"edge scroll direction and bounded speed");check(QueueDropPosition.scroll(150,300,48,12)==0,"no auto scroll in center");
  Looper.prepareMainLooper();Map<String,Prefs> preferences=new HashMap<>();Context context=new ContextWrapper(null){public SharedPreferences getSharedPreferences(String name,int mode){Prefs p=preferences.get(name);if(p==null){p=new Prefs();preferences.put(name,p);}return p.prefs;}};
  context.getSharedPreferences("favorites",0).edit().putString("items","original favorites").apply();context.getSharedPreferences("playback",0).edit().putInt("mode",2).apply();
  Player service=new Player();service.attach(context);service.tracks.addAll(original);service.index=2;
  check(service.moveTrack(original.get(0),original.get(4),true),"production service accepts drag");check(service.current()==original.get(2) && service.index==1,"service tracks playing index after reorder");
  String saved=context.getSharedPreferences("MainActivity",0).getString("queue","");JSONArray restored=new JSONArray(saved);check(restored.length()==5,"all rows persisted");for(int i=0;i<5;i++)check(restored.getJSONObject(i).getString("url").equals(service.tracks.get(i).url),"persisted order matches displayed queue");check(restored.getJSONObject(4).getInt("duration")==180000,"metadata preserved");
  int writes=preferences.get("MainActivity").writes;check(service.moveTrack(service.current(),service.current(),false) && preferences.get("MainActivity").writes==writes,"no-op drag avoids rewriting storage");check(!service.moveTrack(track("gone","gone"),service.current(),false) && preferences.get("MainActivity").writes==writes,"stale drag cannot overwrite saved order");
  check(preferences.get("favorites").writes==1 && preferences.get("playback").writes==1,"favorites and playback mode untouched");
 }
}
