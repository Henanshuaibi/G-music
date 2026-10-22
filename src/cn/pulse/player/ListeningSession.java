package cn.pulse.player;
/** Uses monotonic elapsed time, not media position, so seeks and pauses cannot inflate taste. */
public final class ListeningSession {
 private final ListeningHistory history;private PlayerService.Track track;private long last,total;private boolean active,credited;
 public ListeningSession(ListeningHistory h){history=h;}
 public void sample(PlayerService.Track current,boolean playing,long elapsed,long wall){
  if(track!=null && active && elapsed>=last){long delta=elapsed-last;if(delta>0){total+=delta;boolean qualify=!credited && total>=30000;history.record(track,delta,qualify,wall);if(qualify)credited=true;}}
  if(current==null || track==null || !current.url.equals(track.url)){total=0;credited=false;}
  track=current;active=playing && current!=null;last=elapsed;
 }
 public void end(long elapsed,long wall){sample(null,false,elapsed,wall);}
}
