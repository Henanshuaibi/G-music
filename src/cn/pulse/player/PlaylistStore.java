package cn.pulse.player;
import java.util.*;
import org.json.*;
/** User playlists are independent of likes and the active playback queue. */
public final class PlaylistStore {
 public interface Storage {String read();void write(String text);}
 public static final class Playlist {
  public final String id,name;public final ArrayList<PlayerService.Track> tracks;
  Playlist(String i,String n,List<PlayerService.Track> t){id=i;name=n;tracks=new ArrayList<>(t);}
 }
 private final Storage storage;private final LinkedHashMap<String,Playlist> lists=new LinkedHashMap<>();
 public PlaylistStore(Storage s){storage=s;try{for(Playlist p:decode(new JSONArray(s.read())))lists.put(p.id,p);}catch(Exception ignored){}}
 public ArrayList<Playlist> list(){ArrayList<Playlist> out=new ArrayList<>();for(Playlist p:lists.values())out.add(new Playlist(p.id,p.name,p.tracks));return out;}
 public Playlist get(String id){Playlist p=lists.get(id);return p==null?null:new Playlist(p.id,p.name,p.tracks);}
 static String name(String value){String n=value==null?"":value.trim();if(n.isEmpty() || n.length()>40)throw new IllegalArgumentException("歌单名称需为 1～40 个字");return n;}
 public String create(String value){if(lists.size()>=100)throw new IllegalArgumentException("最多创建 100 个歌单");String id=UUID.randomUUID().toString();lists.put(id,new Playlist(id,name(value),Collections.emptyList()));persist();return id;}
 public boolean rename(String id,String value){Playlist p=lists.get(id);if(p==null)return false;lists.put(id,new Playlist(id,name(value),p.tracks));persist();return true;}
 public boolean delete(String id){if(lists.remove(id)==null)return false;persist();return true;}
 public int add(String id,List<PlayerService.Track> songs){Playlist p=lists.get(id);if(p==null)throw new IllegalArgumentException("歌单已删除");ArrayList<PlayerService.Track> next=new ArrayList<>(p.tracks);int n=LibraryBackup.mergeQueue(next,songs);if(next.size()>1000 || total()-p.tracks.size()+next.size()>10000)throw new IllegalArgumentException("歌单歌曲数量已达上限");if(n>0){lists.put(id,new Playlist(id,p.name,next));persist();}return n;}
 public boolean remove(String id,String source){Playlist p=lists.get(id);if(p==null)return false;ArrayList<PlayerService.Track> next=new ArrayList<>(p.tracks);boolean changed=next.removeIf(t->t.url.equals(source));if(changed){lists.put(id,new Playlist(id,p.name,next));persist();}return changed;}
 private int total(){int n=0;for(Playlist p:lists.values())n+=p.tracks.size();return n;}
 public int merge(JSONArray backup)throws Exception{
  ArrayList<Playlist> incoming=decode(backup);LinkedHashMap<String,Playlist> next=new LinkedHashMap<>(lists);int added=0,total=0;
  for(Playlist p:incoming){Playlist old=next.get(p.id);if(old==null){next.put(p.id,p);added++;}else{ArrayList<PlayerService.Track> songs=new ArrayList<>(old.tracks);LibraryBackup.mergeQueue(songs,p.tracks);if(songs.size()>1000)throw new IllegalArgumentException("合并后单个歌单超过 1000 首");next.put(old.id,new Playlist(old.id,old.name,songs));}}
  for(Playlist p:next.values())total+=p.tracks.size();if(next.size()>100 || total>10000)throw new IllegalArgumentException("合并后歌单数量超过上限");lists.clear();lists.putAll(next);persist();return added;
 }
 static ArrayList<Playlist> decode(JSONArray data)throws Exception{
  if(data.length()>100)throw new IllegalArgumentException("备份超过 100 个歌单");ArrayList<Playlist> out=new ArrayList<>();Set<String> ids=new HashSet<>();int total=0;
  for(int i=0;i<data.length();i++){JSONObject o=data.getJSONObject(i);String id=o.getString("id");if(!id.matches("[A-Za-z0-9_-]{1,80}") || !ids.add(id))throw new IllegalArgumentException("歌单编号无效或重复");ArrayList<PlayerService.Track> songs=LibraryBackup.tracks(o.getJSONArray("tracks"));total+=songs.size();if(songs.size()>1000 || total>10000)throw new IllegalArgumentException("歌单歌曲过多");out.add(new Playlist(id,name(o.getString("name")),songs));}return out;
 }
 static JSONObject track(PlayerService.Track t)throws JSONException{return new JSONObject().put("title",t.title).put("artist",t.artist).put("url",t.url).put("duration",t.expectedMs);}
 private void persist(){try{JSONArray a=new JSONArray();for(Playlist p:lists.values()){JSONArray songs=new JSONArray();for(PlayerService.Track t:p.tracks)songs.put(track(t));a.put(new JSONObject().put("id",p.id).put("name",p.name).put("tracks",songs));}storage.write(a.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
}
