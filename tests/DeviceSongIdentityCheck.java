package cn.pulse.player;
import java.util.*;
/** User-visible duplicate review, including names which must stay distinct. */
public final class DeviceSongIdentityCheck {
 private static int serial,checks;
 private static PlayerService.Track track(String title,String artist,int ms){return new PlayerService.Track(title,artist,"https://example.test/identity/"+(++serial),ms);}
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void match(PlayerService.Track a,PlayerService.Track b,String message){
  check(SongIdentity.possibleDuplicate(a,b),message);
  check(SongIdentity.possibleDuplicate(b,a),"symmetric: "+message);
  check(SongIdentity.findDuplicate(a,Collections.singletonList(b))==b,"favorite hint: "+message);
  check(SongIdentity.duplicatePairs(Arrays.asList(a,b)).size()==1,"review forward: "+message);
  check(SongIdentity.duplicatePairs(Arrays.asList(b,a)).size()==1,"review reverse: "+message);
 }
 private static void distinct(PlayerService.Track a,PlayerService.Track b,String message){
  check(!SongIdentity.possibleDuplicate(a,b) && !SongIdentity.possibleDuplicate(b,a),message);
  check(SongIdentity.duplicatePairs(Arrays.asList(a,b)).isEmpty(),"review stays distinct: "+message);
 }
 public static void main(String[] args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace(System.err);System.exit(1);}}
 static void run(){
  String[][] songs={{"乌梅子酱","李荣浩"},{"晴天","周杰伦"},{"夜曲","周杰伦"},{"Love Story","Taylor Swift"}};
  String[] prefixes={"3-","03 - ","３－","03.","03、","03_","003 ","(03) ","【03】","[03] ","（０３） ","03) ","第3首 ","第３曲：","No. 03 - ","Track 03 - ","#03 ","\uFEFF 03 - "};
  for(String[] song:songs)for(String prefix:prefixes)for(String artist:new String[]{song[1],"本地音乐","B站 · 分享者"}){
   PlayerService.Track numbered=track(prefix+song[0],artist,201000),plain=track(song[0],song[1],200000);
   match(numbered,plain,"sequence/title normalization: "+numbered.title+" / "+artist);
   check(SongIdentity.musicArtist(numbered).equals(artist.equals(song[1])?song[1]:""),"sequence never becomes singer: "+numbered.title);
   check(numbered.title.equals(prefix+song[0]),"matching never rewrites displayed metadata");
  }
  for(int index:new int[]{1,12,321,1000})for(String[] song:songs){
   match(track(index+"-"+song[0],"本地音乐",201000),track(song[0],song[1],200000),"arbitrary sequence number, not a special case: "+index);
  }
  match(track(" 【无损】 ０３．周杰倫 - 晴天.FLAC ","本地音乐",201000),track("晴天","周杰伦",200000),"quality / sequence / singer / extension together");
  match(track("03 - 晴天 - 周杰伦.mp3","周杰伦",201000),track("晴天","周杰伦",200000),"known singer suffix");
  match(track("Love-Story","本地音乐",201000),track("Love Story","Taylor Swift",200000),"hyphen within title is not always an artist delimiter");
  for(String title:new String[]{"7 Years","99 Luftballons","1984","321对不起","1-800-273-8255","3-2"}){
   String artist="歌手甲";
   match(track(title,"本地音乐",201000),track(title,artist,200000),"genuine numeric title: "+title);
   match(track("03 - "+title,"本地音乐",201000),track(title,artist,200000),"numbered genuine numeric title: "+title);
   String shortened=title.replaceFirst("^[0-9]+[- ]?","");
   if(!shortened.isEmpty())distinct(track(title,artist,200000),track(shortened,artist,200000),"do not delete title's own digits: "+title);
   check(SongIdentity.musicArtist(track(title,"本地音乐",200000)).isEmpty(),"numeric title is not a singer: "+title);
  }
  match(track("3-321对不起","本地音乐",201000),track("321对不起","徐良",200000),"un-padded index before numeric song title");
  check(SongIdentity.musicArtist(track("Love-Story","本地音乐",200000)).isEmpty(),"ambiguous bare English dash is not a recommendation artist");
  // Buckets must include every interpretation accepted by possibleDuplicate.
  PlayerService.Track ambiguous=track("Love-Story","本地音乐",201000),spaced=track("Love Story","Taylor Swift",200000),explicit=track("03 - Taylor Swift - Love Story","B站",200000);
  check(SongIdentity.duplicatePairs(Arrays.asList(explicit,ambiguous,spaced)).size()==2,"candidate aliases work in a mixed favorite scan");
  match(track("Taylor Swift - Love Story","B站",201000),track("Love Story","Taylor Swift",200000),"existing artist-title extraction");
  distinct(track("03 - 晴天","其他歌手",200000),track("晴天","周杰伦",200000),"different singers");
  distinct(track("其他歌手 - 晴天","本地音乐",200000),track("晴天","周杰伦",200000),"embedded different singers");
  for(String version:new String[]{"Live","翻唱","Remix","伴奏","不插电","加速","降速"}){
   distinct(track("03 - 晴天 ("+version+")","周杰伦",200000),track("晴天","周杰伦",200000),"different version: "+version);
   match(track("03 - 晴天 ("+version+")","周杰伦",201000),track("晴天 ("+version+")","周杰伦",200000),"same version: "+version);
  }
  distinct(track("03 - 晴天","周杰伦",100000),track("晴天","周杰伦",200000),"short edit");
  distinct(track("03 - 晴天","本地音乐",0),track("晴天","B站",0),"insufficient metadata");
  distinct(track("03 - 晴天","周杰伦",200000),track("晴天之外","周杰伦",200000),"no fuzzy merging of different titles");
  // A matched row remains a useful reference for later rows with less metadata.
  PlayerService.Track a=track("夜曲","周杰伦",200000),b=track("03 - 夜曲","本地音乐",199000),c=track("【04】夜曲","本地音乐",195000);
  check(!SongIdentity.possibleDuplicate(a,c),"duration boundary for weak metadata");
  ArrayList<PlayerService.Track> saved=new ArrayList<>(Arrays.asList(a,b,c));
  ArrayList<PlayerService.Track[]> pairs=SongIdentity.duplicatePairs(saved);
  check(pairs.size()==2 && pairs.get(1)[0]==c && pairs.get(1)[1]==b,"review checks all earlier rows, not only unmatched representatives");
  check(saved.size()==3 && saved.get(0)==a && saved.get(1)==b && saved.get(2)==c,"review preserves favorite sources and order");
  System.out.println("PASS: "+checks+" duplicate identity checks across multiple songs, sequence formats, numeric titles, artist/title ambiguity, versions, duration, and complete review indexing");
 }
}
