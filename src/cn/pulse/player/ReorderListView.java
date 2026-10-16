package cn.pulse.player;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local drag shadow + insertion marker; commit once on drop, leave canceled drags unchanged. */
public final class ReorderListView extends ListView {
 public interface DropListener {boolean move(PlayerService.Track source,PlayerService.Track anchor,boolean after);}
 private List<PlayerService.Track> items;private DropListener listener;private DragToken active;private float pointerY,markerY;private int slot=-1;private boolean inside;
 private View dragHost;private final int[] hostLocation=new int[2],listLocation=new int[2];
 private final Paint marker=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final DragAutoScroll autoScroll=new DragAutoScroll(new DragAutoScroll.Host(){
  public void post(Runnable task,int delayMs){postDelayed(task,delayMs);}
  public void remove(Runnable task){removeCallbacks(task);}
  public boolean valid(){return inside && ReorderListView.this.valid();}
  public int height(){return getHeight();}
  public int edge(){return Math.min(dp(64),getHeight()/3);}
  public int maxStep(){return dp(16);}
  public void scrollBy(int delta){if(canScrollVertically(delta<0?-1:1))scrollListBy(delta);updateMarker();}
 });
 private static final class DragToken {
  final ReorderListView owner;final SwipeTrackRow row;final PlayerService.Track track;final ArrayList<PlayerService.Track> snapshot;
  DragToken(ReorderListView owner,SwipeTrackRow row,PlayerService.Track track,List<PlayerService.Track> items){this.owner=owner;this.row=row;this.track=track;snapshot=new ArrayList<>(items);}
 }
 public ReorderListView(Context context){super(context);marker.setColor(Color.WHITE);marker.setStrokeWidth(dp(2));setDividerHeight(dp(1));}
 private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 public void enableReorder(List<PlayerService.Track> rows,int color,DropListener onDrop){items=rows;listener=onDrop;marker.setColor(color);}
 /** The dialog also accepts drags above/below the list, keeping edge scrolling reachable. */
 public void setDragHost(View host){
  if(dragHost!=null)dragHost.setOnDragListener(null);dragHost=host;
  if(host!=null)host.setOnDragListener((view,event)->handleDrag(event,view));
 }
 public boolean startReorder(SwipeTrackRow row,PlayerService.Track track){
  if(listener==null || items==null || !items.contains(track) || active!=null)return false;
  row.prepareForReorder();DragToken token=new DragToken(this,row,track,items);active=token;
  View.DragShadowBuilder shadow=new View.DragShadowBuilder(row){public void onProvideShadowMetrics(Point size,Point touch){size.set(row.getWidth(),row.getHeight());touch.set(Math.max(0,row.getWidth()-dp(34)),row.getHeight()/2);}};
  // The list stays attached even after the original row scrolls out of view.
  boolean started=startDragAndDrop(null,shadow,token,0);
  if(started)row.setAlpha(.4f);else reset();return started;
 }
 private boolean valid(){return active!=null && items!=null && active.snapshot.equals(items);}
 public boolean onDragEvent(DragEvent event){return handleDrag(event,this);}
 private boolean handleDrag(DragEvent event,View receiver){
  Object local=event.getLocalState();if(!(local instanceof DragToken) || ((DragToken)local).owner!=this)return false;DragToken token=(DragToken)local;
  switch(event.getAction()){
   case DragEvent.ACTION_DRAG_STARTED:return active==token && valid();
   case DragEvent.ACTION_DRAG_ENTERED:return true;
   case DragEvent.ACTION_DRAG_LOCATION:
    if(active!=token)return false;locate(event,receiver);return true;
   case DragEvent.ACTION_DRAG_EXITED:
    // No coordinates are available here. Retain the edge position until the
    // dialog gets its next location, or ENDED cancels the drag outside the window.
    return true;
   case DragEvent.ACTION_DROP:
    if(active!=token || !valid()){reset();return false;}locate(event,receiver);int insertion=inside?slot:-1;reset();if(insertion<0 || items.isEmpty())return false;
    PlayerService.Track anchor=items.get(Math.min(insertion,items.size()-1));return listener.move(token.track,anchor,insertion==items.size());
   case DragEvent.ACTION_DRAG_ENDED:reset();return true;
  }
  return true;
 }
 private void locate(DragEvent event,View receiver){
  receiver.getLocationOnScreen(hostLocation);getLocationOnScreen(listLocation);
  float x=event.getX()+hostLocation[0]-listLocation[0];pointerY=event.getY()+hostLocation[1]-listLocation[1];
  inside=x>=0 && x<=getWidth();
  if(inside){updateMarker();autoScroll.move(pointerY);}else{autoScroll.stop();slot=-1;invalidate();}
 }
 private void updateMarker(){
  if(!valid()){slot=-1;invalidate();return;}int n=getChildCount();int[] tops=new int[n],bottoms=new int[n];for(int i=0;i<n;i++){View child=getChildAt(i);tops[i]=child.getTop();bottoms[i]=child.getBottom();}
  int first=getFirstVisiblePosition();slot=QueueDropPosition.slot(pointerY,first,items.size(),tops,bottoms);
  if(slot>=0){int row=slot-first;markerY=row<n?tops[Math.max(0,row)]:bottoms[n-1];markerY=Math.max(dp(2),Math.min(getHeight()-dp(2),markerY));}invalidate();
 }
 protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(inside && slot>=0 && active!=null){canvas.drawLine(dp(12),markerY,getWidth()-dp(12),markerY,marker);canvas.drawCircle(dp(12),markerY,dp(3),marker);}}
 private void reset(){autoScroll.stop();if(active!=null)active.row.setAlpha(1f);active=null;inside=false;slot=-1;invalidate();}
 public void cancelReorder(){if(active!=null){reset();cancelDragAndDrop();}}
 protected void onDetachedFromWindow(){cancelReorder();setDragHost(null);super.onDetachedFromWindow();}
}
