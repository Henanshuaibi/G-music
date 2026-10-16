package cn.pulse.player;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Right swipe reveals an explicit action; tapping and holding have separate meanings. */
public final class SwipeTrackRow extends FrameLayout {
 public interface RevealListener {void revealed(SwipeTrackRow row);}
 public interface ReorderActions {boolean start(SwipeTrackRow row);void step(int direction);}
 private final LinearLayout front;private final SwipeGesture gesture;private final float actionWidth;
 private final Runnable tap,hold;private final RevealListener revealed;
 private float downX,downY;private boolean held;
 private final Runnable longPress;
 public SwipeTrackRow(Context context,String title,String subtitle,boolean liked,String action,Runnable play,Runnable favorite,Runnable remove,Runnable download,RevealListener onReveal){
  this(context,title,subtitle,liked,action,play,favorite,remove,download,onReveal,null);
 }
 public SwipeTrackRow(Context context,String title,String subtitle,boolean liked,String action,Runnable play,Runnable favorite,Runnable remove,Runnable download,RevealListener onReveal,ReorderActions reorder){
  super(context);tap=play;hold=favorite;revealed=onReveal;actionWidth=dp(download==null?76:148);gesture=new SwipeGesture(ViewConfiguration.get(context).getScaledTouchSlop(),actionWidth);
  setClipChildren(true);
  LinearLayout actions=new LinearLayout(context);addView(actions,new FrameLayout.LayoutParams((int)actionWidth,-1,Gravity.LEFT));
  TextView delete=new TextView(context);delete.setText(action);delete.setTextSize(13);delete.setTextColor(Color.WHITE);delete.setGravity(Gravity.CENTER);delete.setContentDescription(action+" "+title);delete.setBackgroundColor(0xff81383f);actions.addView(delete,new LinearLayout.LayoutParams(0,-1,1));delete.setOnClickListener(v->remove.run());
  if(download!=null){TextView save=new TextView(context);save.setText("下载");save.setTextSize(13);save.setTextColor(Color.WHITE);save.setGravity(Gravity.CENTER);save.setContentDescription("下载 "+title);save.setBackgroundColor(0xff2e595d);actions.addView(save,new LinearLayout.LayoutParams(0,-1,1));save.setOnClickListener(v->{close();download.run();});}
  front=new LinearLayout(context);front.setOrientation(LinearLayout.HORIZONTAL);front.setGravity(Gravity.CENTER_VERTICAL);front.setPadding(dp(12),dp(12),dp(10),dp(12));front.setMinimumHeight(dp(70));front.setBackgroundColor(0xff121212);front.setHapticFeedbackEnabled(false);
  LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);front.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  TextView name=new TextView(context);name.setText(title+(liked?"  ♥":""));name.setTextSize(15);name.setTextColor(Color.WHITE);name.setMaxLines(2);labels.addView(name);
  TextView detail=new TextView(context);detail.setText(subtitle);detail.setTextSize(11);detail.setTextColor(0xffaaaaaa);detail.setMaxLines(1);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(detail);
  if(reorder!=null){
   View handle=new View(context){final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    protected void onDraw(Canvas canvas){super.onDraw(canvas);ink.setColor(isPressed()?Color.WHITE:0xffaaaaaa);ink.setStrokeWidth(dp(2));ink.setStrokeCap(Paint.Cap.ROUND);float x=getWidth()/2f,y=getHeight()/2f;for(int line=-1;line<=1;line++)canvas.drawLine(x-dp(9),y+line*dp(6),x+dp(9),y+line*dp(6),ink);}
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,"上移一位"));info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,"下移一位"));}
    public boolean performAccessibilityAction(int action,android.os.Bundle args){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){reorder.step(-1);return true;}if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){reorder.step(1);return true;}return super.performAccessibilityAction(action,args);}
   };
   handle.setBackgroundResource(android.R.drawable.list_selector_background);handle.setContentDescription("调整播放顺序："+title+"，按住后上下拖动");handle.setTooltipText("按住后上下拖动排序");handle.setOnClickListener(v->Toast.makeText(context,"按住右侧三横线，再上下拖动",Toast.LENGTH_SHORT).show());handle.setOnLongClickListener(v->{prepareForReorder();if(!reorder.start(this))Toast.makeText(context,"列表已更新，请重新拖动",Toast.LENGTH_SHORT).show();return true;});
   front.addView(handle,new LinearLayout.LayoutParams(dp(48),dp(48)));
  }
  addView(front,new FrameLayout.LayoutParams(-1,-2));
  longPress=()->{if(gesture.stationary()){held=true;front.performLongClick();}};
  front.setContentDescription(title+(download==null?"，点击切换，右划"+action:"，点击播放，长按切换喜欢，右划下载或"+action));
  front.setOnClickListener(v->{if(front.getTranslationX()>1)close();else tap.run();});if(hold!=null)front.setOnLongClickListener(v->{hold.run();return true;});
  front.setOnTouchListener((v,event)->touch(event));
 }
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private boolean touch(MotionEvent event){
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    front.animate().cancel();downX=event.getRawX();downY=event.getRawY();held=false;gesture.down(front.getTranslationX());if(hold!=null)front.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());return true;
   case MotionEvent.ACTION_MOVE:
    if(held)return true;boolean wasHorizontal=gesture.horizontal;gesture.move(event.getRawX()-downX,event.getRawY()-downY);if(gesture.moved)front.removeCallbacks(longPress);
    if(gesture.horizontal){if(!wasHorizontal)revealed.revealed(this);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);front.setTranslationX(gesture.offset);}return true;
   case MotionEvent.ACTION_UP:
    front.removeCallbacks(longPress);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    if(held)return true;if(gesture.horizontal){if(gesture.reveal()){revealed.revealed(this);front.animate().translationX(actionWidth).setDuration(140).start();}else close();}else if(gesture.stationary())front.performClick();return true;
   case MotionEvent.ACTION_CANCEL:
    front.removeCallbacks(longPress);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);close();return true;
   case MotionEvent.ACTION_POINTER_DOWN:
    held=true;front.removeCallbacks(longPress);close();return true;
  }return true;
 }
 public void prepareForReorder(){front.removeCallbacks(longPress);front.animate().cancel();front.setTranslationX(0);}
 public void close(){front.animate().translationX(0).setDuration(140).start();}
 protected void onDetachedFromWindow(){front.removeCallbacks(longPress);front.animate().cancel();front.setTranslationX(0);super.onDetachedFromWindow();}
}
