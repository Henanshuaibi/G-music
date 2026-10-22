package cn.pulse.player;
import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.*;
public class PlayerService extends Service {
 public static class Track {
  public final String title,artist,url;public final int expectedMs;
  public Track(String t,String a,String u){this(t,a,u,0);}
  public Track(String t,String a,String u,int duration){title=t;artist=a;url=u;expectedMs=duration;}
 }
 public final ArrayList<Track> tracks=new ArrayList<>();
 public int index=-1;
 public String status="导入歌曲，点亮声音";
 private AudioPlayer player;
 private final HashSet<String> removedFromQueue=new HashSet<>();
 private PlaylistStore playlists;private ListeningHistory history;private ListeningSession listening;
 private FavoriteStore favorites;private int playbackMode;private final Random selectionRandom=new Random();
 private String mediaSource;private Map<String,String> mediaHeaders=new HashMap<>();private PcmAnalyzer analyzer;
 private final ExecutorService resolver=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Set<Runnable> listeners=new LinkedHashSet<>();
 private final Set<Object> analysisOwners=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
 private final Runnable releaseIdleAnalysis=()->{if(analysisOwners.isEmpty())releaseAnalysis();};
 private int generation=0;
 private MediaSession session;
 private AudioManager audio;
 private AudioFocusRequest focus;
 private boolean ready=false,loading=false,seeking=false,playWhenReady=false,foreground=false,resumeFocus=false;
 private int savedPosition=0,savedDuration=0,seekTarget=0;
 private final Runnable checkpoint=new Runnable(){public void run(){if(playing()){savePlayback();main.postDelayed(this,5000);}}};
 public class LocalBinder extends Binder {public PlayerService get(){return PlayerService.this;}}
 public IBinder onBind(Intent i){return new LocalBinder();}
 private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};
 public void onCreate(){
  super.onCreate();SharedPreferences favoritePrefs=getSharedPreferences("favorites",0);favorites=new FavoriteStore(new FavoriteStore.Storage(){public String read(){return favoritePrefs.getString("items","[]");}public void write(String data){favoritePrefs.edit().putString("items",data).apply();}});playbackMode=getSharedPreferences("playback",0).getInt("mode",0);removedFromQueue.addAll(favoritePrefs.getStringSet("removedFromQueue",Collections.emptySet()));audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  musicStores();restoreQueue();
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(this::audioFocusChanged,main).build();
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("music","音乐播放",NotificationManager.IMPORTANCE_LOW));
  session=new MediaSession(this,"G music");session.setCallback(new MediaSession.Callback(){
   public void onCustomAction(String action,Bundle extras){if("favorite".equals(action))notificationFavorite(extras==null?null:extras.getString("source"));else if("playback_mode".equals(action))cyclePlaybackMode();}
   public void onPlay(){resume();} public void onPause(){pause();} public void onSkipToNext(){next(1);} public void onSkipToPrevious(){next(-1);} public void onSeekTo(long p){seek((int)p);} public void onStop(){pause();stopForeground(true);foreground=false;stopSelf();}
  });session.setActive(true);
  IntentFilter f=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,f);
  update();
 }
 public int onStartCommand(Intent i,int flags,int id){if(i!=null){String a=i.getAction();if("toggle".equals(a))toggle();else if("next".equals(a))next(1);else if("prev".equals(a))next(-1);else if("favorite".equals(a))notificationFavorite(i.getStringExtra("source"));else if("playback_mode".equals(a))cyclePlaybackMode();}return START_NOT_STICKY;}
 public Track current(){return index>=0 && index<tracks.size()?tracks.get(index):null;}
 public boolean playing(){try{return ready && player!=null && player.playing();}catch(IllegalStateException e){return false;}}
 public boolean playRequested(){return playWhenReady;}
 public int position(){if(ready && !seeking)try{savedPosition=Math.max(0,player.position());}catch(IllegalStateException ignored){}return savedPosition;}
 public int duration(){if(ready)try{savedDuration=Math.max(0,player.duration());}catch(IllegalStateException ignored){}return savedDuration;}
 public int audioSession(){return ready?player.session():0;}
 public void clear(){for(Track t:tracks)removedFromQueue.add(t.url);persistRemoved();resetPlayer();tracks.clear();index=-1;status="导入歌曲，点亮声音";persistQueue();update();stopPlaybackService();}
 public void add(List<Track> t){tracks.addAll(t);for(Track track:t)removedFromQueue.remove(track.url);persistRemoved();persistQueue();}
 public void play(int n){load(n,0);}
 private void load(int n,int position){
  if(n<0 || n>=tracks.size())return;int knownDuration=n==index?savedDuration:0;resetPlayer();final int ticket=generation;index=n;savedPosition=Math.max(0,position);savedDuration=Math.max(knownDuration,Math.max(0,current().expectedMs));loading=true;playWhenReady=true;status="正在载入…";persistQueue();
  try{
   player=createPlayer(new AudioPlayer.Listener(){
    public void prepared(){if(ticket!=generation)return;onPrepared(ticket);}
    public void completed(){if(ticket==generation && ready && playWhenReady && !seeking)autoNext();}
    public void seekCompleted(){if(ticket!=generation || !ready || !seeking)return;if(savedPosition!=seekTarget){issueSeek();return;}seeking=false;savedPosition=player.position();if(analyzer!=null)analyzer.seek(savedPosition);startWhenReady();savePlayback();}
    public void failed(){loadError(ticket,"无法播放：链接失效、格式不支持或网络不可用");}
   });
   ensureForeground();update();
  }catch(Exception e){loadError(ticket,"无法启动播放器，请重试");return;}
  final AudioPlayer target=player;final String source=current().url;
  if(source.startsWith("bili:") || source.startsWith("bili-link:")){
   status="正在读取 B站分集…";update();
   resolver.execute(()->{try{BiliClient.Result result=BiliClient.open(source.startsWith("bili:")?source.substring(5):BiliClient.shortLink(source.substring(10)));main.post(()->{if(ticket!=generation || player!=target)return;
    int at=index;tracks.remove(at);tracks.addAll(at,result.parts);index=at+result.selected;favorites.refresh(current());persistQueue();
    Map<String,String> headers=new HashMap<>();headers.put("Referer","https://www.bilibili.com/");headers.put("User-Agent","Mozilla/5.0");setSource(ticket,target,result.media,headers);
   });}catch(Exception e){main.post(()->loadError(ticket,e.getMessage()==null?"B站音频暂不可用":e.getMessage()));}});
  }else setSource(ticket,target,source,new HashMap<>());
 }
 AudioPlayer createPlayer(AudioPlayer.Listener listener){return new AudioPlayer.Native(this,listener);}
 void ensureForeground(){startService(new Intent(this,PlayerService.class));startForeground(1,notification());foreground=true;}
 boolean requestFocus(){return audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
 void stopPlaybackService(){stopForeground(true);foreground=false;stopSelf();}
 private void resetPlayer(){if(listening!=null)listening.end(SystemClock.elapsedRealtime(),System.currentTimeMillis());generation++;releaseAnalysis();mediaSource=null;ready=false;loading=false;seeking=false;playWhenReady=false;resumeFocus=false;savedPosition=0;savedDuration=0;if(player!=null){player.release();player=null;}}
 private void onPrepared(int ticket){
  if(current().expectedMs>0 && player.duration()+5000<current().expectedMs){loadError(ticket,"实际音源短于标注分集，已停止播放，请换一个结果");return;}
  ready=true;loading=false;savedDuration=player.duration();savedPosition=clampPosition(savedPosition);
  if(savedPosition>0)issueSeek();else startWhenReady();savePlayback();
 }
 private void setSource(int ticket,AudioPlayer target,String source,Map<String,String> headers){
  if(ticket!=generation)return;try{mediaSource=source;mediaHeaders=headers;target.source(this,source,headers);target.prepare();}catch(Exception e){loadError(ticket,"无法打开音频，请重新导入或检查链接");}
 }
 private void loadError(int ticket,String message){if(ticket!=generation)return;generation++;releaseAnalysis();ready=false;loading=false;seeking=false;playWhenReady=false;resumeFocus=false;mediaSource=null;if(player!=null){player.release();player=null;}status=message;savePlayback();update();}
 private void startWhenReady(){
  if(!ready || seeking)return;
  if(!playWhenReady){status="已暂停";update();return;}
  if(!playing()){
   if(!requestFocus()){playWhenReady=false;status="其他应用正在占用音频";savePlayback();update();return;}
   ensureForeground();player.start();
  }
  status="正在播放";update();
 }
 void audioFocusChanged(int change){
  if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(ready)player.volume(.2f);}
  else if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){boolean again=change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && playWhenReady;pause();resumeFocus=again;}
  else if(change==AudioManager.AUDIOFOCUS_GAIN){if(ready)player.volume(1);if(resumeFocus){resumeFocus=false;resume();}}
 }
 // A short grace period lets portrait and landscape share the warmed decoder.
 // Each surface releases its own token; a late detach cannot close another surface's decoder.
 public void retainAnalysis(Object owner){analysisOwners.add(owner);main.removeCallbacks(releaseIdleAnalysis);}
 public void releaseAnalysis(Object owner){if(analysisOwners.remove(owner) && analysisOwners.isEmpty())main.postDelayed(releaseIdleAnalysis,1500);}
 public boolean analysisFrame(AudioReaction reaction,long now){if(!ready || mediaSource==null || analysisOwners.isEmpty())return false;if(analyzer==null)analyzer=new PcmAnalyzer(this,mediaSource,new HashMap<>(mediaHeaders),position());return analyzer.feed(position(),reaction,now);}
 public String analysisStatus(){return analyzer==null?"等待播放音频":analyzer.status();}
 public void releaseAnalysis(){main.removeCallbacks(releaseIdleAnalysis);if(analyzer!=null){analyzer.close();analyzer=null;}}
 public void addListener(Runnable listener){listeners.add(listener);}
 public void removeListener(Runnable listener){listeners.remove(listener);}
 // Queue order and bookmark share one transaction so duplicate occurrences restore exactly.
 private SharedPreferences.Editor bookmark(SharedPreferences.Editor edit){Track t=current();return edit.putInt("queueIndex",t==null?-1:index).putString("queueSource",t==null?"":t.url).putInt("queuePosition",t==null?0:position()).putInt("queueDuration",t==null?0:duration());}
 public void savePlayback(){sampleListening();bookmark(getSharedPreferences("MainActivity",0).edit()).apply();}
 private void persistQueue(){try{JSONArray a=new JSONArray();for(Track t:tracks)a.put(new JSONObject().put("title",t.title).put("artist",t.artist).put("url",t.url).put("duration",t.expectedMs));bookmark(getSharedPreferences("MainActivity",0).edit().putString("queue",a.toString())).apply();}catch(Exception ignored){}}
 void restoreQueue(){
  SharedPreferences prefs=getSharedPreferences("MainActivity",0);ArrayList<Track> restored=new ArrayList<>();
  try{JSONArray a=new JSONArray(prefs.getString("queue","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);restored.add(new Track(o.getString("title"),o.getString("artist"),o.getString("url"),Math.max(0,o.optInt("duration"))));}}catch(Exception e){return;}
  tracks.addAll(restored);int remembered=prefs.getInt("queueIndex",-1);
  if(remembered>=0 && remembered<tracks.size() && tracks.get(remembered).url.equals(prefs.getString("queueSource",""))){index=remembered;savedDuration=Math.max(0,prefs.getInt("queueDuration",current().expectedMs));savedPosition=clampPosition(prefs.getInt("queuePosition",0));status="已恢复上次播放，点播放继续";}
 }
 private void musicStores(){
  SharedPreferences p=getSharedPreferences("library",0);
  if(playlists==null)playlists=new PlaylistStore(new PlaylistStore.Storage(){public String read(){return p.getString("playlists","[]");}public void write(String s){p.edit().putString("playlists",s).apply();}});
  if(history==null){history=new ListeningHistory(new ListeningHistory.Storage(){public String read(){return p.getString("history","[]");}public void write(String s){p.edit().putString("history",s).apply();}});listening=new ListeningSession(history);}
 }
 private void sampleListening(){if(listening!=null)listening.sample(current(),playing() && !seeking && playWhenReady,SystemClock.elapsedRealtime(),System.currentTimeMillis());}
 public PlaylistStore playlists(){musicStores();return playlists;}
 public ArrayList<ListeningHistory.Entry> listeningHistory(){musicStores();sampleListening();return history.ranked(System.currentTimeMillis());}
 public void clearListeningHistory(){musicStores();listening.end(SystemClock.elapsedRealtime(),System.currentTimeMillis());history.clear();listening=new ListeningSession(history);sampleListening();}
 public void playPlaylist(List<Track> songs,int selected){if(songs.isEmpty() || selected<0 || selected>=songs.size())return;ArrayList<Track> copy=new ArrayList<>(songs);resetPlayer();tracks.clear();tracks.addAll(copy);index=-1;for(Track t:copy)removedFromQueue.remove(t.url);persistRemoved();play(selected);}
 public ArrayList<Track> favorites(){return favorites.list();}
 public boolean canFavorite(Track track){return track!=null && !track.url.startsWith("bili-link:") && (!track.url.startsWith("bili:") || track.url.contains("?cid="));}
 public Track duplicateFavorite(Track track){return track==null?null:SongIdentity.findDuplicate(track,favorites.list());}
 public boolean isFavorite(Track track){return track!=null && favorites.contains(track.url);}
 public boolean toggleFavorite(Track track){if(!canFavorite(track))return false;boolean added=favorites.toggle(track);update();return added;}
 private void persistRemoved(){getSharedPreferences("favorites",0).edit().putStringSet("removedFromQueue",new HashSet<>(removedFromQueue)).apply();}
 public boolean removeTrack(Track target){
  boolean continuing=playWhenReady;QueueEdits.Removal removal=QueueEdits.remove(tracks,index,target);if(removal==null)return false;
  removedFromQueue.add(removal.track.url);persistRemoved();index=removal.nextIndex;
  if(removal.currentRemoved || tracks.isEmpty()){
   resetPlayer();if(current()!=null)savedDuration=current().expectedMs;
   if(tracks.isEmpty()){status="播放列表已清空";stopPlaybackService();}
   else if(continuing)play(index);else status="已移出歌曲，点播放继续";
  }
  persistQueue();update();return true;
 }
 public boolean moveTrack(Track source,Track anchor,boolean after){
  QueueEdits.Movement result=QueueEdits.move(tracks,index,source,anchor,after);if(result==null)return false;
  index=result.currentIndex;if(result.changed)persistQueue();return true;
 }
 public int[] restoreLibrary(LibraryBackup.Data data){musicStores();try{playlists.merge(data.playlists);history.merge(data.history);}catch(Exception e){throw new IllegalArgumentException("歌单或播放记录合并失败："+e.getMessage());}int queued=LibraryBackup.mergeQueue(tracks,data.queue),liked=favorites.merge(data.favorites);playbackMode=data.mode;getSharedPreferences("playback",0).edit().putInt("mode",playbackMode).apply();if(data.visuals!=null)data.visuals.save(this);persistQueue();update();return new int[]{queued,liked};}
 public int playbackMode(){return playbackMode;}
 public void cyclePlaybackMode(){playbackMode=PlaybackOrder.nextMode(playbackMode);getSharedPreferences("playback",0).edit().putInt("mode",playbackMode).apply();update();}
 public void playTrack(Track track){if(track==null)return;if(removedFromQueue.remove(track.url))persistRemoved();int exact=tracks.indexOf(track);if(exact>=0){play(exact);return;}for(int i=0;i<tracks.size();i++)if(tracks.get(i).url.equals(track.url)){play(i);return;}tracks.add(track);persistQueue();play(tracks.size()-1);}
 private void autoNext(){if(listening!=null)listening.end(SystemClock.elapsedRealtime(),System.currentTimeMillis());int n=PlaybackOrder.choose(tracks.size(),index,playbackMode,1,true,selectionRandom);if(n==index && ready){seek(0);resume();}else if(n>=0)play(n);
 }
 public void toggle(){if(playWhenReady)pause();else resume();}
 public void resume(){
  resumeFocus=false;
  if(current()==null){if(!tracks.isEmpty())play(0);return;}
  if(!ready && !loading){load(index,savedPosition);return;}
  playWhenReady=true;if(loading || seeking){status="正在载入…";ensureForeground();update();}else startWhenReady();
 }
 public void pause(){playWhenReady=false;resumeFocus=false;if(playing())player.pause();status="已暂停";savePlayback();update();}
 public void next(int step){int n=PlaybackOrder.choose(tracks.size(),index,playbackMode,step,false,selectionRandom);if(n>=0)play(n);}
 private int clampPosition(int p){int length=duration();return Math.max(0,length>0?Math.min(p,length):p);}
 private void issueSeek(){seeking=true;seekTarget=savedPosition;player.seek(seekTarget);}
 public void seek(int p){if(current()==null)return;savedPosition=clampPosition(p);if(ready && !seeking)issueSeek();savePlayback();update();}
 private void notificationFavorite(String source){Track track=current();if(track==null || source==null || !track.url.equals(source)){update();return;}if(!canFavorite(track)){android.widget.Toast.makeText(this,"歌曲载入后可喜欢",android.widget.Toast.LENGTH_SHORT).show();return;}if(!isFavorite(track) && duplicateFavorite(track)!=null){android.widget.Toast.makeText(this,"已有相似收藏，可在我的喜欢中检查重复",android.widget.Toast.LENGTH_LONG).show();return;}boolean added=toggleFavorite(track);android.widget.Toast.makeText(this,added?"已加入我的喜欢":"已取消喜欢",android.widget.Toast.LENGTH_SHORT).show();}
 private PendingIntent favoriteAction(){Track track=current();String source=track==null?"":track.url;Intent intent=new Intent(this,PlayerService.class).setAction("favorite").setData(Uri.parse("gmusic://favorite/"+Uri.encode(source))).putExtra("source",source);return PendingIntent.getService(this,4,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private PendingIntent action(String a,int code){return PendingIntent.getService(this,code,new Intent(this,PlayerService.class).setAction(a),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private Notification notification(){
  Track t=current();return new Notification.Builder(this,"music").setSmallIcon(R.drawable.icon).setContentTitle(t==null?"G music":t.title).setContentText(t==null?status:t.artist+" · "+status)
   .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"上一首",action("prev",1)).build())
   .addAction(new Notification.Action.Builder(playWhenReady?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playWhenReady?"暂停":"播放",action("toggle",2)).build())
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"下一首",action("next",3)).build())
   .addAction(new Notification.Action.Builder(isFavorite(t)?R.drawable.heart_filled:R.drawable.heart_outline,isFavorite(t)?"取消喜欢":"喜欢",favoriteAction()).build())
   .addAction(new Notification.Action.Builder(PlaybackControls.modeIcon(playbackMode),PlaybackOrder.label(playbackMode),action("playback_mode",5)).build())
   .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).setOngoing(playWhenReady).setVisibility(Notification.VISIBILITY_PUBLIC).build();
 }
 void update(){
  sampleListening();
  main.removeCallbacks(checkpoint);if(playing())main.postDelayed(checkpoint,5000);
  for(Runnable listener:new ArrayList<>(listeners))listener.run();
  if(session==null)return;
  Track t=current();if(t!=null)session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,t.title).putString(MediaMetadata.METADATA_KEY_ARTIST,t.artist).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());
  PlaybackState.Builder state=new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO).setState(playWhenReady && (loading || seeking)?PlaybackState.STATE_BUFFERING:playing()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position(),playing()?1:0);
  PlaybackControls.addCustomActions(state,canFavorite(t)?t.url:null,isFavorite(t),playbackMode);session.setPlaybackState(state.build());
  if(foreground)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification());
 }
 public void onTaskRemoved(Intent root){savePlayback();super.onTaskRemoved(root);}
 public void onDestroy(){savePlayback();main.removeCallbacks(checkpoint);listeners.clear();analysisOwners.clear();releaseAnalysis();generation++;resolver.shutdownNow();unregisterReceiver(noisy);audio.abandonAudioFocusRequest(focus);if(player!=null)player.release();session.release();super.onDestroy();}
}
