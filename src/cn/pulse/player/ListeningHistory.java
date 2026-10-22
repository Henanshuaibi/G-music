package cn.pulse.player;
import java.util.*;
import org.json.*;
/** Bounded local listening history; seeking does not contribute listening time. */
public final class ListeningHistory {
 public interface Storage {String read();void write(String text);}
 public static final class Entry {public final PlayerService.Track track;public long millis,last;public int plays;Entry(PlayerService.Track t,long ms,int n,long at){track=t;millis=ms;plays=n;last=at;}}
 private final Storage storage;private final LinkedHashMap<String,Entry> rows=new LinkedHashMap<>();
 public ListeningHistory(Storage s){storage=s;try{for(Entry e:decode(new JSONArray(s.read())))rows.put(e.track.url,e);}catch(Exception ignored){}}
 public ArrayList<Entry> ranked(long now){ArrayList<Entry> out=new ArrayList<>();for(Entry e:rows.values())if(e.plays>0)out.add(new Entry(e.track,e.millis,e.plays,e.last));out.sort((a,b)->Double.compare(score(b,now),score(a,now)));return out;}
 private static double score(Entry e,long now){double age=Math.max(0,now-e.last)/86400000.0;return (Math.log1p(e.plays)*3+Math.log1p(e.millis/60000.0))/(1+age/14);}
 public void record(PlayerService.Track t,long delta,boolean qualified,long at){if(t==null || delta<=0)return;Entry old=rows.remove(t.url);rows.put(t.url,new Entry(t,Math.min(315360000000L,(old==null?0:old.millis)+delta),Math.min(1000000,(old==null?0:old.plays)+(qualified?1:0)),Math.max(0,at)));while(rows.size()>300)rows.remove(rows.keySet().iterator().next());persist();}
 public void clear(){rows.clear();persist();}
 public void merge(JSONArray backup)throws Exception{for(Entry e:decode(backup)){Entry old=rows.get(e.track.url);if(old==null || e.last>old.last)rows.put(e.track.url,new Entry(e.track,Math.max(e.millis,old==null?0:old.millis),Math.max(e.plays,old==null?0:old.plays),Math.max(e.last,old==null?0:old.last)));else{old.millis=Math.max(old.millis,e.millis);old.plays=Math.max(old.plays,e.plays);}}ArrayList<Entry> sorted=new ArrayList<>(rows.values());sorted.sort((a,b)->Long.compare(a.last,b.last));rows.clear();for(Entry e:sorted)rows.put(e.track.url,e);while(rows.size()>300)rows.remove(rows.keySet().iterator().next());persist();}
 static ArrayList<Entry> decode(JSONArray a)throws Exception{if(a.length()>300)throw new IllegalArgumentException("播放记录过多");ArrayList<Entry> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);PlayerService.Track t=LibraryBackup.tracks(new JSONArray().put(o.getJSONObject("track"))).get(0);long ms=o.getLong("millis"),last=o.getLong("last");int n=o.getInt("plays");if(ms<0 || ms>315360000000L || last<0 || n<0 || n>1000000 || !seen.add(t.url))throw new IllegalArgumentException("播放记录无效");out.add(new Entry(t,ms,n,last));}return out;}
 private void persist(){try{JSONArray a=new JSONArray();for(Entry e:rows.values())a.put(new JSONObject().put("track",PlaylistStore.track(e.track)).put("millis",e.millis).put("plays",e.plays).put("last",e.last));storage.write(a.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
}
