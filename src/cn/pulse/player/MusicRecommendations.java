package cn.pulse.player;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
/** Small, explainable discovery from local taste; only search keywords leave the device. */
public final class MusicRecommendations {
 public static final class Seed {public final String query,reason;Seed(String q,String r){query=q;reason=r;}}
 public static final class Suggestion {public final PlayerService.Track track;public final String reason;Suggestion(PlayerService.Track t,String r){track=t;reason=r;}}
 public static ArrayList<Seed> seeds(List<ListeningHistory.Entry> history,List<PlayerService.Track> favorites){
  LinkedHashMap<String,Seed> out=new LinkedHashMap<>();for(ListeningHistory.Entry e:history)addSeed(out,e.track,"常听");for(PlayerService.Track t:favorites)addSeed(out,t,"喜欢");return new ArrayList<>(out.values());
 }
 private static void addSeed(Map<String,Seed> out,PlayerService.Track t,String kind){String artist=SongIdentity.musicArtist(t);String term=artist.isEmpty()?t.title.replaceAll("[【\\[].*?[】\\]]","").trim():artist;if(term.isEmpty() || term.startsWith("B站视频") || term.equals("网络音频"))return;term=term.substring(0,Math.min(60,term.length()));String key=SongIdentity.normalized(term);if(!out.containsKey(key))out.put(key,new Seed(term+" 歌曲", "因为你"+kind+"「"+term+"」"));}
 public static ArrayList<Suggestion> local(List<ListeningHistory.Entry> history,List<PlayerService.Track> favorites,List<PlayerService.Track> pool,String current,Set<String> blocked,int batch,long now){
  Map<String,Double> taste=new HashMap<>();Map<String,Long> recent=new HashMap<>();Set<String> liked=new HashSet<>();
  for(ListeningHistory.Entry e:history){String a=SongIdentity.musicArtist(e.track);if(!a.isEmpty())taste.put(a,taste.getOrDefault(a,0.0)+(Math.log1p(e.plays)*3+Math.log1p(e.millis/60000.0))/(1+Math.max(0,now-e.last)/1209600000.0));recent.put(sourceKey(e.track.url),e.last);}
  for(PlayerService.Track t:favorites){liked.add(t.url);String a=SongIdentity.musicArtist(t);if(!a.isEmpty())taste.put(a,taste.getOrDefault(a,0.0)+1);}
  final Map<String,Double> weights=new HashMap<>();LinkedHashMap<String,Suggestion> candidates=new LinkedHashMap<>();
  for(PlayerService.Track t:pool){String key=sourceKey(t.url);if(key.equals(sourceKey(current==null?"":current)) || blocked.contains(key) || candidates.containsKey(key))continue;Long last=recent.get(key);if(last!=null && now-last<86400000L)continue;
   String a=SongIdentity.musicArtist(t);double weight=taste.getOrDefault(a,0.0);if(weight==0 && !liked.contains(t.url))continue;
   String reason=weight>0?"根据你常听 / 喜欢的歌手「"+a+"」":"从你的喜欢中重新发现";weights.put(key,weight+(liked.contains(t.url)?.25:0));candidates.put(key,new Suggestion(t,reason));
  }
  ArrayList<Suggestion> all=new ArrayList<>(candidates.values());all.sort((a,b)->Double.compare(weights.get(sourceKey(b.track.url)),weights.get(sourceKey(a.track.url))));ArrayList<Suggestion> out=new ArrayList<>();if(all.isEmpty())return out;
  int start=(batch*12)%all.size();for(int i=0;i<Math.min(12,all.size());i++)out.add(all.get((start+i)%all.size()));return out;
 }
 static String sourceKey(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^bili:(BV[0-9A-Za-z]{10})(?:\\?.*)?$").matcher(value);return m.matches()?"bili:"+m.group(1):value;}
 public static boolean eligible(PlayerService.Track candidate,List<PlayerService.Track> known,Set<String> blocked,List<Suggestion> accepted){
  if(candidate.expectedMs<45000 || candidate.expectedMs>720000 || candidate.title.matches("(?is).*(合集|歌单|串烧|盘点|一小时|小时纯享|教程|教学|reaction).*"))return false;
  String key=sourceKey(candidate.url);if(blocked.contains(key))return false;
  for(PlayerService.Track t:known)if(sourceKey(t.url).equals(key) || SongIdentity.possibleDuplicate(candidate,t))return false;
  for(Suggestion s:accepted)if(sourceKey(s.track.url).equals(key) || SongIdentity.possibleDuplicate(candidate,s.track))return false;
  return true;
 }
 static int duration(String s){try{String[] p=s.split(":");if(p.length<2 || p.length>3)return 0;long seconds=0;for(String n:p){int v=Integer.parseInt(n);if(v<0)return 0;seconds=seconds*60+v;}return (int)Math.min(Integer.MAX_VALUE,seconds*1000);}catch(Exception e){return 0;}}
 public static ArrayList<Suggestion> fetch(List<Seed> seeds,List<PlayerService.Track> known,Set<String> blocked,int batch,AtomicBoolean canceled)throws Exception{
  ArrayList<Suggestion> out=new ArrayList<>();int succeeded=0;Exception failure=null;
  int start=seeds.isEmpty()?0:(batch*2)%seeds.size();
  for(int n=0;n<Math.min(3,seeds.size()) && !canceled.get();n++){
   Seed seed=seeds.get((start+n)%seeds.size());HttpURLConnection c=null;
   try{c=(HttpURLConnection)new URL("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword="+URLEncoder.encode(seed.query,"UTF-8")+"&page="+(1+(batch%3))).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Referer","https://www.bilibili.com/");
    int status=c.getResponseCode();if(status!=200)throw new IOException("B 站搜索返回 HTTP "+status);StringBuilder body=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;while((line=r.readLine())!=null){if(canceled.get())return out;body.append(line);if(body.length()>1500000)throw new IOException("结果过大");}}
    JSONObject json=new JSONObject(body.toString());if(json.optInt("code",-1)!=0)throw new IOException("搜索暂不可用");JSONArray a=json.getJSONObject("data").optJSONArray("result");succeeded++;int perSeed=0;
    if(a!=null)for(int i=0;i<a.length() && perSeed<6 && !canceled.get();i++){JSONObject row=a.getJSONObject(i);String bv=row.optString("bvid");if(!bv.matches("BV[0-9A-Za-z]{10}"))continue;String title=android.text.Html.fromHtml(row.optString("title"),android.text.Html.FROM_HTML_MODE_LEGACY).toString();PlayerService.Track t=new PlayerService.Track(title,"B站 · "+row.optString("author"),"bili:"+bv,duration(row.optString("duration")));if(eligible(t,known,blocked,out)){out.add(new Suggestion(t,seed.reason));perSeed++;}}
   }catch(Exception e){failure=e;}finally{if(c!=null)c.disconnect();}
  }
  if(succeeded==0 && !canceled.get())throw new IOException("暂时无法获取新歌："+(failure==null?"搜索服务不可用":failure.getMessage()),failure);
  return out;
 }
}
