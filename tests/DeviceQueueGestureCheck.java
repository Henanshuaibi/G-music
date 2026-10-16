package cn.pulse.player;
import java.util.*;
public final class DeviceQueueGestureCheck {
 private static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
 public static void main(String[] args){try{run();System.exit(0);}catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 private static void run(){
  PlayerService.Track a=new PlayerService.Track("a","artist","local:a"),b=new PlayerService.Track("b","artist","local:b"),c=new PlayerService.Track("c","artist","local:c");
  ArrayList<PlayerService.Track> q=new ArrayList<>(Arrays.asList(a,b,c));QueueEdits.Removal r=QueueEdits.remove(q,1,a);check(!r.currentRemoved && r.nextIndex==0 && q.get(r.nextIndex)==b,"remove before current preserves current song");
  q=new ArrayList<>(Arrays.asList(a,b,c));r=QueueEdits.remove(q,0,c);check(!r.currentRemoved && r.nextIndex==0 && q.get(0)==a,"remove after current preserves current song");
  q=new ArrayList<>(Arrays.asList(a,b,c));r=QueueEdits.remove(q,1,b);check(r.currentRemoved && r.nextIndex==1 && q.get(1)==c,"remove current selects next song");
  q=new ArrayList<>(Arrays.asList(a,b,c));r=QueueEdits.remove(q,2,c);check(r.currentRemoved && r.nextIndex==0,"remove last current wraps to first");
  q=new ArrayList<>(Collections.singletonList(a));r=QueueEdits.remove(q,0,a);check(q.isEmpty() && r.currentRemoved && r.nextIndex==-1,"remove only song leaves stopped queue");
  q=new ArrayList<>(Arrays.asList(a,b));r=QueueEdits.remove(q,-1,b);check(r.nextIndex==-1 && !r.currentRemoved,"unselected queue stays unselected");
  check(QueueEdits.remove(q,0,c)==null && q.size()==1,"stale nonexistent selection cannot remove a different song");
  r=QueueEdits.remove(q,0,new PlayerService.Track("renamed","artist",a.url));check(r.currentRemoved && q.isEmpty(),"metadata replacement maps by stable source");
  SwipeGesture gesture=new SwipeGesture(8,76);gesture.down(0);gesture.move(4,3);check(gesture.stationary(),"finger jitter keeps tap and hold valid");
  gesture.move(60,3);check(gesture.horizontal && gesture.reveal() && !gesture.stationary(),"right swipe reveals deletion without playing");
  gesture.move(300,5);check(gesture.offset==76,"reveal bounded to action width");
  gesture.down(76);gesture.move(-70,2);check(gesture.horizontal && !gesture.reveal(),"left swipe closes revealed action");
  gesture.down(0);gesture.move(4,30);gesture.move(70,35);check(gesture.vertical && !gesture.horizontal && !gesture.reveal(),"vertical scrolling cannot turn into deletion gesture");
  gesture.down(0);gesture.move(14,1);check(gesture.horizontal && !gesture.reveal(),"short swipe returns closed");
  gesture.down(0);gesture.move(-40,1);check(!gesture.horizontal && !gesture.stationary(),"unsupported left swipe cannot trigger playback or like");
  final String[] data={"[]"};FavoriteStore store=new FavoriteStore(new FavoriteStore.Storage(){public String read(){return data[0];}public void write(String value){data[0]=value;}});store.toggle(a);q=new ArrayList<>(Collections.singletonList(a));QueueEdits.remove(q,0,a);check(new FavoriteStore(new FavoriteStore.Storage(){public String read(){return data[0];}public void write(String value){data[0]=value;}}).contains(a.url),"removal from queue preserves favorite");
  System.out.println("PASS: current/previous/next/last queue deletion, stable source mapping, favorite preservation, right swipe, close, scroll direction lock, tap/hold separation");
 }
}
