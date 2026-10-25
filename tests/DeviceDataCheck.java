package cn.pulse.player;
import java.io.*;
import java.util.*;
import org.json.*;
public final class DeviceDataCheck {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 private static PlayerService.Track track(String title,String artist,int ms,String id){return new PlayerService.Track(title,artist,"https://example.test/"+id,ms);}
 public static void main(String[] args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace(System.err);System.exit(1);}}
 static void run()throws Exception{
  PlayerService.Track a=track("001.周杰伦-晴天【4K无损音质】","B站 · UP甲",270000,"a"),b=track("周杰倫《晴天》","B站 · UP乙",271000,"b"),c=track("晴天","周杰伦",270000,"c");
  check(SongIdentity.possibleDuplicate(a,b),"different uploaders and traditional titles");check(SongIdentity.possibleDuplicate(a,c),"local metadata vs video title");check(!SongIdentity.possibleDuplicate(a,track("晴天","其他歌手",270000,"other")),"distinct known artist must remain separate");check(!SongIdentity.possibleDuplicate(a,track("周杰伦-晴天 (Live)","B站",270000,"live")),"live version not merged");check(!SongIdentity.possibleDuplicate(a,track("周杰伦-晴天","B站",130000,"short")),"short edit not duplicate");check(SongIdentity.possibleDuplicate(track("徐良&小凌 - 321對不起","B站",206000,"xu1"),track("321对不起","徐良",205000,"xu2")),"keep numeric titles, collaborative artist");check(!SongIdentity.possibleDuplicate(track("歌曲","B站",0,"x"),track("歌曲","B站",0,"y")),"unknown singer and length insufficient");
  ArrayList<PlayerService.Track[]> pairs=SongIdentity.duplicatePairs(Arrays.asList(a,b,c,track("夜曲","周杰伦",220000,"n")));check(pairs.size()==2 && pairs.get(0)[1]==a,"duplicate grouping preserves original sources");
  PlayerService.Track plum=track("乌梅子酱","李荣浩",257000,"plum");
  for(String prefix:new String[]{"3-","03 - ","3—","3–","3－"})for(String artist:new String[]{"李荣浩","本地音乐","B站 · UP甲"}){
   PlayerService.Track numbered=track(prefix+"乌梅子酱",artist,258000,"numbered-"+prefix+artist);
   check(SongIdentity.possibleDuplicate(numbered,plum) && SongIdentity.possibleDuplicate(plum,numbered),"numbered title matches in both directions: "+prefix+artist);
   check(SongIdentity.findDuplicate(numbered,Collections.singletonList(plum))==plum,"adding numbered favorite finds existing song");
   ArrayList<PlayerService.Track> saved=new ArrayList<>(Arrays.asList(numbered,plum));
   ArrayList<PlayerService.Track[]> numberedPairs=SongIdentity.duplicatePairs(saved);
   check(numberedPairs.size()==1 && numberedPairs.get(0)[0]==plum && numberedPairs.get(0)[1]==numbered && saved.size()==2,"favorite scan finds numbered title and preserves both sources");
   check(SongIdentity.musicArtist(numbered).equals(artist.equals("李荣浩")?"李荣浩":""),"track number never becomes artist");
  }
  check(SongIdentity.possibleDuplicate(track("3-李荣浩 - 乌梅子酱","本地音乐",257000,"numbered-artist"),plum),"strip track number before extracting singer");
  check(!SongIdentity.possibleDuplicate(track("3-乌梅子酱","其他歌手",257000,"plum-other"),plum),"number prefix keeps different artists separate");
  check(!SongIdentity.possibleDuplicate(track("3-乌梅子酱 (Live)","李荣浩",257000,"plum-live"),plum),"number prefix keeps live version separate");
  check(!SongIdentity.possibleDuplicate(track("3-乌梅子酱","李荣浩",130000,"plum-short"),plum),"number prefix keeps short edit separate");
  check(!SongIdentity.possibleDuplicate(track("321对不起","徐良",205000,"numeric-title"),track("对不起","徐良",205000,"different-title")),"digits belonging to song title remain significant");
  JSONArray queue=new JSONArray().put(json(a)).put(json(b)).put(json(a)),favorites=new JSONArray().put(json(a)).put(json(c));String backup=LibraryBackup.encode(queue,favorites,2);LibraryBackup.Data data=LibraryBackup.read(new ByteArrayInputStream(backup.getBytes("UTF-8")));check(data.queue.size()==2 && data.favorites.size()==2 && data.mode==2,"backup roundtrip and stable source deduplication");check(data.favorites.get(0).title.equals(a.title),"unicode metadata preserved");ArrayList<PlayerService.Track> current=new ArrayList<>(Collections.singletonList(c));check(LibraryBackup.mergeQueue(current,data.queue)==2 && current.get(0)==c,"merge keeps playing row and ordering");check(LibraryBackup.mergeQueue(current,data.queue)==0,"repeat restore idempotent");
  String[] disk={"[]"};FavoriteStore store=new FavoriteStore(new FavoriteStore.Storage(){public String read(){return disk[0];}public void write(String text){disk[0]=text;}});store.toggle(b);check(store.merge(data.favorites)==2 && store.contains(b.url),"restore preserves existing favorite");check(store.merge(data.favorites)==0 && store.list().size()==3,"no duplicate source after second restore");FavoriteStore restored=new FavoriteStore(new FavoriteStore.Storage(){public String read(){return disk[0];}public void write(String text){disk[0]=text;}});check(restored.list().size()==3,"favorite merge persisted");
  JSONObject badUrl=new JSONObject(backup);badUrl.getJSONArray("queue").getJSONObject(0).put("url","file:///private/key");for(String invalid:new String[]{"{}",backup.replace("gmusic.library.v1","wrong"),badUrl.toString(),new JSONObject(backup).put("playbackMode",7).toString()}){boolean rejected=false;try{LibraryBackup.parse(invalid);}catch(Exception e){rejected=true;}check(rejected,"invalid backup rejected before restore");}
  check(LibraryBackup.validSource("bili:BV1FPjy6TEiE?cid=123") && LibraryBackup.validSource("content://media/external/audio/media/12") && LibraryBackup.validSource("bili-link:https://b23.tv/abc"),"supported persisted sources");check(!LibraryBackup.validSource("https://user:pass@example.test/secret"),"do not restore credential-bearing URLs");
  System.out.println("PASS: numbered favorite titles and artist extraction, duplicate hints across source/title/artist variants, separate live/artist/short edits, backup Unicode roundtrip, preserving merge, repeat restore, persistent favorites, invalid backup rejection");
 }
 static JSONObject json(PlayerService.Track t)throws Exception{return new JSONObject().put("title",t.title).put("artist",t.artist).put("url",t.url).put("duration",t.expectedMs);}
}
