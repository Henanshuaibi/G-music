package cn.pulse.player;

/** Deterministic clock drives the production timer, not just scroll-direction arithmetic. */
public final class DragAutoScrollCheck {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static final class Host implements DragAutoScroll.Host {
  long now,due;Runnable pending;boolean valid=true;int offset=0,frames=0;
  final int rowHeight=70,totalRows=100,viewport=210;
  public void post(Runnable task,int delay){check(pending==null,"only one pending tick");pending=task;due=now+delay;}
  public void remove(Runnable task){if(pending==task)pending=null;}
  public boolean valid(){return valid;}
  public int height(){return viewport;}
  public int edge(){return 64;}
  public int maxStep(){return 16;}
  public void scrollBy(int delta){offset=Math.max(0,Math.min(totalRows*rowHeight-viewport,offset+delta));frames++;}
  void advance(long ms){long target=now+ms;while(pending!=null && due<=target){now=due;Runnable r=pending;pending=null;r.run();}now=target;}
 }
 public static void main(String[] args){
  Host h=new Host();DragAutoScroll drag=new DragAutoScroll(h);
  // A location every 8 ms used to cancel the 32 ms tick indefinitely.
  for(int i=0;i<250;i++){drag.move(205+i%3);h.advance(8);}
  check(h.frames>=60 && h.offset>700,"frequent moving pointer scrolls past more than three songs");
  // One event then a stationary finger must traverse all 100 tracks.
  drag.move(260);h.advance(20000);check(h.offset==6790,"stationary finger below list reaches last song");
  int[] tops={0,70,140},bottoms={70,140,210};check(QueueDropPosition.slot(260,h.offset/70,100,tops,bottoms)==100,"drop after continuously scrolling reaches queue end");
  drag.move(-50);h.advance(20000);check(h.offset==0,"stationary finger above list reaches first song");
  check(QueueDropPosition.slot(-50,0,100,tops,bottoms)==0,"drop above list inserts at head");
  drag.move(208);h.advance(400);int down=h.offset;drag.move(1);h.advance(200);check(h.offset<down,"changing direction works without lifting finger");
  drag.move(105);int center=h.offset;h.advance(1000);check(h.offset==center,"middle of list stops scrolling");
  drag.move(209);h.advance(100);drag.stop();int stopped=h.offset;h.advance(1000);check(h.pending==null && h.offset==stopped,"drop/cancel removes tick and stops scrolling");
  drag.move(209);h.advance(64);check(h.offset>stopped,"next drag restarts timer");h.valid=false;h.advance(64);check(h.pending==null,"stale/dismissed drag stops scheduling");
  h.valid=true;drag.move(-10000);h.advance(4000);check(h.offset==0,"out-of-bounds pointer clamps speed and stops at first song");drag.stop();
  System.out.println("PASS: 8 ms pointer events never starve 32 ms scroll ticks; stationary edges traverse 100 songs; reversal, center, boundaries, cancel and restart");
 }
}
