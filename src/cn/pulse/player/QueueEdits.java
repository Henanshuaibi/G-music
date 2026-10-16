package cn.pulse.player;
import java.util.*;
public final class QueueEdits {
 public static final class Removal {
  public final PlayerService.Track track;public final int nextIndex;public final boolean currentRemoved;
  Removal(PlayerService.Track t,int next,boolean current){track=t;nextIndex=next;currentRemoved=current;}
 }
 public static final class Movement {
  public final int from,to,currentIndex;public final boolean changed;
  Movement(int from,int to,int current){this.from=from;this.to=to;currentIndex=current;changed=from!=to;}
 }
 /** Use exact row objects: equal URLs may deliberately occur more than once in the queue. */
 public static Movement move(List<PlayerService.Track> tracks,int current,PlayerService.Track source,PlayerService.Track anchor,boolean after){
  if(source==null || anchor==null)return null;int from=tracks.indexOf(source),at=tracks.indexOf(anchor);if(from<0 || at<0)return null;
  int to=at+(after?1:0);if(from<to)to--;if(source==anchor)to=from;
  int next=current;
  if(from!=to){PlayerService.Track item=tracks.remove(from);tracks.add(to,item);
   if(current==from)next=to;else if(from<current && current<=to)next=current-1;else if(to<=current && current<from)next=current+1;
  }
  return new Movement(from,to,next);
 }
 public static Removal remove(List<PlayerService.Track> tracks,int current,PlayerService.Track target){
  int at=tracks.indexOf(target);if(at<0)for(int i=0;i<tracks.size();i++)if(tracks.get(i).url.equals(target.url)){at=i;break;}
  if(at<0)return null;PlayerService.Track removed=tracks.remove(at);boolean wasCurrent=at==current;
  int next=tracks.isEmpty()?-1:wasCurrent?at%tracks.size():at<current?current-1:current;
  return new Removal(removed,next,wasCurrent);
 }
}
