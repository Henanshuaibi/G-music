package cn.pulse.player;
import java.util.*;
import org.json.*;
/** Durable favorites independent of the current playback queue. */
public final class FavoriteStore {
 public interface Storage {String read();void write(String data);}
 private final Storage storage;
 private final LinkedHashMap<String,PlayerService.Track> saved=new LinkedHashMap<>();
 public FavoriteStore(Storage backend){storage=backend;try{JSONArray a=new JSONArray(storage.read());for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String url=o.optString("url");if(!url.isEmpty())saved.put(url,new PlayerService.Track(o.optString("title"),o.optString("artist"),url,o.optInt("duration")));}}catch(Exception ignored){}}
 public boolean contains(String source){return saved.containsKey(source);}
 public boolean toggle(PlayerService.Track track){if(saved.containsKey(track.url)){saved.remove(track.url);persist();return false;}saved.put(track.url,track);persist();return true;}
 public ArrayList<PlayerService.Track> list(){ArrayList<PlayerService.Track> result=new ArrayList<>(saved.values());Collections.reverse(result);return result;}
 public int merge(List<PlayerService.Track> tracks){int added=0;for(PlayerService.Track t:tracks)if(!saved.containsKey(t.url)){saved.put(t.url,t);added++;}if(added>0)persist();return added;}
 public void refresh(PlayerService.Track track){if(saved.containsKey(track.url)){saved.put(track.url,track);persist();}}
 private void persist(){try{JSONArray a=new JSONArray();for(PlayerService.Track t:saved.values())a.put(new JSONObject().put("title",t.title).put("artist",t.artist).put("url",t.url).put("duration",t.expectedMs));storage.write(a.toString());}catch(Exception ignored){}}
}
