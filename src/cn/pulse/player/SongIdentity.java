package cn.pulse.player;
import java.util.*;
import java.util.regex.*;
/** Metadata-only duplicate hints: never delete or merge a different source automatically. */
public final class SongIdentity {
 private static final Map<PlayerService.Track,Description> cache=Collections.synchronizedMap(new WeakHashMap<>());
 private static final ThreadLocal<android.icu.text.Transliterator> chinese=new ThreadLocal<>();
 private static final Pattern quality=Pattern.compile("(?i)[\\[【(][^\\]】)]*(?:4k|1080p|720p|hifi|无损|高清|音质|字幕|完整版|官方mv)[^\\]】)]*[\\]】)]");
 private static final Pattern extension=Pattern.compile("(?i)\\.(?:mp3|flac|m4a|wav|ogg|aac|mp4|opus|ape|wma)$");
 private static final Pattern explicitIndex=Pattern.compile("(?i)^(?:\\([0-9]{1,6}\\)|\\[[0-9]{1,6}\\]|【[0-9]{1,6}】|[0-9]{1,6}\\)|第[0-9]{1,6}[首曲]|(?:no\\.?|track)\\s*[0-9]{1,6}(?=[\\s.:、_—–-]|$)|#[0-9]{1,6}(?=[\\s.:、_—–-]|$))[\\s.:、_—–-]*(.+)$");
 private static final Pattern bareIndex=Pattern.compile("^([0-9]{1,6})([.、_ —–-]+)(.+)$");
 private static final class Candidate {
  final String title,artist;
  Candidate(String title,String artist){this.title=normalized(title);this.artist=artist;}
 }
 private static final class Description {
  final ArrayList<Candidate> candidates=new ArrayList<>();
  final String version;
  String artist;
  Description(String version,String artist){this.version=version;this.artist=artist;}
  void add(String title,String artist){
   Candidate candidate=new Candidate(title,artist);if(candidate.title.isEmpty())return;
   for(Candidate previous:candidates)if(previous.title.equals(candidate.title) && previous.artist.equals(candidate.artist))return;
   candidates.add(candidate);
  }
 }
 static String simple(String text){if(android.os.Build.VERSION.SDK_INT>=29)try{android.icu.text.Transliterator t=chinese.get();if(t==null){t=android.icu.text.Transliterator.getInstance("Traditional-Simplified");chinese.set(t);}return t.transliterate(text);}catch(Exception ignored){}return text;}
 private static String canonical(String text){return simple(java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)).replaceAll("[\\p{Cf}]","").replaceAll("[\\p{Z}\\s]+"," ").trim();}
 static String normalized(String text){return canonical(text).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");}
 private static String clean(String text){
  String result=canonical(text);
  // Clear decoration before parsing artist/title, consistently for every matching entry point.
  for(int i=0;i<4;i++){
   String previous=result;
   result=extension.matcher(quality.matcher(result).replaceAll("").trim()).replaceFirst("").trim();
   Matcher explicit=explicitIndex.matcher(result),bare=bareIndex.matcher(result);
   if(explicit.matches())result=explicit.group(1).trim();
   else if(bare.matches()){
    String number=bare.group(1),separator=bare.group(2),rest=bare.group(3).trim();
    boolean padded=number.length()>1 && number.charAt(0)=='0';
    // "7 Years", "99 Luftballons" and "1-800-273-8255" are titles, not indexes.
    if(padded || (!separator.trim().isEmpty() && !rest.isEmpty() && (!Character.isDigit(rest.charAt(0)) || rest.matches(".*\\p{L}.*"))))result=rest;
   }
   if(result.equals(previous))break;
  }
  return result;
 }
 static String version(String text){String s=canonical(text).toLowerCase(Locale.ROOT);StringBuilder v=new StringBuilder();String[][] kinds={{"live","live|现场|演唱会"},{"cover","cover|翻唱"},{"remix","remix|混音"},{"dj","\\bdj\\b"},{"instrumental","instrumental|伴奏|纯音乐"},{"acoustic","acoustic|不插电"},{"speed","sped|加速|变速"},{"slow","slowed|慢速|降速"}};for(String[] kind:kinds)if(Pattern.compile(kind[1]).matcher(s).find())v.append(kind[0]).append('/');return v.toString();}
 private static boolean genericArtist(String artist){return artist.isEmpty() || artist.startsWith("B站") || artist.equals("本地音乐") || artist.equals("网络直链");}
 private static boolean plausibleArtist(String artist){return !normalized(artist).isEmpty() && artist.length()<=40 && !artist.matches("[0-9\\p{P}\\s]+") && version(artist).isEmpty();}
 private static Description describe(PlayerService.Track track){
  Description cached=cache.get(track);if(cached!=null)return cached;
  String title=clean(track.title),artist=canonical(track.artist);
  if(genericArtist(artist))artist="";
  Description value=new Description(version(track.title),artist);
  Matcher quoted=Pattern.compile("《([^》]+)》").matcher(title);
  Matcher bracket=Pattern.compile("^[【\\[]([^】\\]]{1,40})[】\\]]\\s*(.+)$").matcher(title);
  if(quoted.find()){
   String before=title.substring(0,quoted.start()).replaceAll("[【】\\[\\]：:\\s—–-]","");
   String inferred=artist.isEmpty() && plausibleArtist(before)?before:artist;
   value.add(quoted.group(1),inferred);value.artist=inferred;
  }else if(bracket.matches() && plausibleArtist(bracket.group(1)) && (artist.isEmpty() || normalized(artist).equals(normalized(bracket.group(1))))){
   String inferred=artist.isEmpty()?bracket.group(1):artist;
   value.add(bracket.group(2),inferred);value.artist=inferred;
  }else{
   Matcher split=Pattern.compile("^(.+?)\\s*[-—–]\\s*(.+)$").matcher(title);
   if(split.matches()){
    String left=split.group(1).trim(),right=split.group(2).trim();
    if(!artist.isEmpty()){
     if(normalized(left).equals(normalized(artist)))value.add(right,artist);
     else if(normalized(right).equals(normalized(artist)))value.add(left,artist);
    }else if(plausibleArtist(left)){
     value.add(right,left);
     // Bare English "Love-Story" is ambiguous; don't recommend a made-up singer.
     if(left.indexOf(' ')>=0 || left.matches(".*[^\\x00-\\x7F].*") || title.matches(".*\\s[-—–].*|.*[-—–]\\s.*"))value.artist=left;
    }
   }
  }
  // A dash may belong to the song title. Retain that interpretation as well.
  value.add(title,artist);cache.put(track,value);return value;
 }
 public static String musicArtist(PlayerService.Track track){String a=describe(track).artist.trim();return a.length()<=40?a:"";}
 private static boolean matches(Candidate x,Candidate y,PlayerService.Track a,PlayerService.Track b){
  if(!x.title.equals(y.title))return false;
  boolean known=!x.artist.isEmpty() && !y.artist.isEmpty();
  if(known){boolean overlap=false;for(String one:x.artist.split("[&、,，/]"))for(String two:y.artist.split("[&、,，/]"))if(!normalized(one).isEmpty() && normalized(one).equals(normalized(two)))overlap=true;if(!overlap)return false;}
  if(a.expectedMs>0 && b.expectedMs>0)return Math.abs((long)a.expectedMs-b.expectedMs)<=(known?10000:4000);
  return known;
 }
 public static boolean possibleDuplicate(PlayerService.Track a,PlayerService.Track b){
  if(a.url.equals(b.url))return true;
  Description x=describe(a),y=describe(b);if(!x.version.equals(y.version))return false;
  for(Candidate one:x.candidates)for(Candidate two:y.candidates)if(matches(one,two,a,b))return true;
  return false;
 }
 public static ArrayList<PlayerService.Track[]> duplicatePairs(List<PlayerService.Track> saved){
  Map<String,ArrayList<PlayerService.Track>> buckets=new HashMap<>();ArrayList<PlayerService.Track[]> pairs=new ArrayList<>();
  for(PlayerService.Track track:saved){
   Description identity=describe(track);LinkedHashSet<String> keys=new LinkedHashSet<>();
   for(Candidate candidate:identity.candidates)keys.add(candidate.title+"|"+identity.version);
   PlayerService.Track match=null;
   for(String key:keys){ArrayList<PlayerService.Track> bucket=buckets.get(key);if(bucket!=null)match=findDuplicate(track,bucket);if(match!=null)break;}
   if(match!=null)pairs.add(new PlayerService.Track[]{track,match});
   // Keep matched rows too: a later row may match their duration/artist more closely.
   for(String key:keys){ArrayList<PlayerService.Track> bucket=buckets.get(key);if(bucket==null){bucket=new ArrayList<>();buckets.put(key,bucket);}bucket.add(track);}
  }
  return pairs;
 }
 public static PlayerService.Track findDuplicate(PlayerService.Track track,List<PlayerService.Track> saved){for(PlayerService.Track other:saved)if(!other.url.equals(track.url) && possibleDuplicate(track,other))return other;return null;}
}
