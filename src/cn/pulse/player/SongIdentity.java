package cn.pulse.player;
import java.util.*;
import java.util.regex.*;
/** Metadata-only duplicate hints: never delete or merge a different source automatically. */
public final class SongIdentity {
 private static final Map<PlayerService.Track,String[]> cache=Collections.synchronizedMap(new WeakHashMap<>());
 private static final ThreadLocal<android.icu.text.Transliterator> chinese=new ThreadLocal<>();
 static String simple(String text){if(android.os.Build.VERSION.SDK_INT>=29)try{android.icu.text.Transliterator t=chinese.get();if(t==null){t=android.icu.text.Transliterator.getInstance("Traditional-Simplified");chinese.set(t);}return t.transliterate(text);}catch(Exception ignored){}return text;}
 static String normalized(String text){return simple(java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");}
 private static String clean(String text){return simple(text).replaceFirst("(?i)\\.(mp3|flac|m4a|wav|ogg|aac|mp4)$","").replaceFirst("^\\s*\\d+[.、_\\s]+","").replaceAll("(?i)[\\[【（(][^\\]】）)]*(?:4k|1080p|720p|hifi|无损|高清|音质|字幕|完整版|官方mv)[^\\]】）)]*[\\]】）)]","").trim();}
 static String version(String text){String s=simple(text).toLowerCase(Locale.ROOT);StringBuilder v=new StringBuilder();String[][] kinds={{"live","live|现场|演唱会"},{"cover","cover|翻唱"},{"remix","remix|混音"},{"dj","\\bdj\\b"},{"instrumental","instrumental|伴奏|纯音乐"},{"acoustic","acoustic|不插电"},{"speed","sped|加速|变速"},{"slow","slowed|慢速|降速"}};for(String[] kind:kinds)if(Pattern.compile(kind[1]).matcher(s).find())v.append(kind[0]).append('/');return v.toString();}
 private static String[] describe(PlayerService.Track track){String[] cached=cache.get(track);if(cached!=null)return cached;String t=clean(track.title),artist=track.artist;boolean generic=artist.isEmpty() || artist.startsWith("B站") || artist.equals("本地音乐") || artist.equals("网络直链");if(generic)artist="";
  Matcher quoted=Pattern.compile("《([^》]+)》").matcher(t);if(quoted.find()){String before=t.substring(0,quoted.start()).replaceAll("[【】\\[\\]：:\\s-]","");if(artist.isEmpty() && before.length()<=20)artist=before;t=quoted.group(1);}
  else{Matcher bracket=Pattern.compile("^[【\\[]([^】\\]]{1,16})[】\\]]\\s*(.+)$").matcher(t);if(bracket.matches()){if(artist.isEmpty())artist=bracket.group(1);t=bracket.group(2);}else{String[] parts=t.split("\\s*[-—–]\\s*",2);if(parts.length==2 && (generic || normalized(parts[0]).equals(normalized(artist)))){artist=parts[0];t=parts[1];}}}
  String[] value=new String[]{normalized(t),simple(artist),version(track.title)};cache.put(track,value);return value;
 }
 public static String musicArtist(PlayerService.Track track){String a=describe(track)[1].trim();return a.length()<=40?a:"";}
 public static boolean possibleDuplicate(PlayerService.Track a,PlayerService.Track b){
  if(a.url.equals(b.url))return true;
  String[] x=describe(a),y=describe(b);if(!x[2].equals(y[2]))return false;if(x[0].isEmpty() || !x[0].equals(y[0]))return false;
  boolean known=!x[1].isEmpty() && !y[1].isEmpty();if(known){boolean overlap=false;for(String one:x[1].split("[&、,，/]"))for(String two:y[1].split("[&、,，/]"))if(!normalized(one).isEmpty() && normalized(one).equals(normalized(two)))overlap=true;if(!overlap)return false;}
  if(a.expectedMs>0 && b.expectedMs>0)return Math.abs((long)a.expectedMs-b.expectedMs)<=(known?10000:4000);
  return known;
 }
 public static ArrayList<PlayerService.Track[]> duplicatePairs(List<PlayerService.Track> saved){Map<String,ArrayList<PlayerService.Track>> buckets=new HashMap<>();ArrayList<PlayerService.Track[]> pairs=new ArrayList<>();for(PlayerService.Track t:saved){String[] identity=describe(t);String key=identity[0]+"|"+identity[2];ArrayList<PlayerService.Track> bucket=buckets.get(key);if(bucket==null){bucket=new ArrayList<>();buckets.put(key,bucket);}PlayerService.Track match=findDuplicate(t,bucket);if(match!=null)pairs.add(new PlayerService.Track[]{t,match});else bucket.add(t);}return pairs;}
 public static PlayerService.Track findDuplicate(PlayerService.Track track,List<PlayerService.Track> saved){for(PlayerService.Track other:saved)if(!other.url.equals(track.url) && possibleDuplicate(track,other))return other;return null;}
}
