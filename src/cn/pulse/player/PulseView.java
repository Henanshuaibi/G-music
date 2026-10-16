package cn.pulse.player;
import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.*;
public class PulseView extends View {
 private final ReactionArt art=new ReactionArt();private final AudioReaction reaction=new AudioReaction();private final AudioReaction.Frame audio=new AudioReaction.Frame();
 private VisualSettings visualSettings;
 private final Object analysisOwner=new Object();
 private PlayerService owner;private int session=-1;private boolean active,hasFrame;private long lastCapture;private Runnable doubleTap;private final GestureDetector gestures;
 public boolean showLabel=true;public String message="等待播放";public int accent=0xffeeeeee;
 public PulseView(Context c){super(c);visualSettings=VisualSettings.load(c);setClickable(true);setContentDescription("双击特效进入或退出横屏");gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}public boolean onDoubleTap(MotionEvent e){if(doubleTap!=null)doubleTap.run();return true;}});}
 public void setVisualSettings(VisualSettings settings){visualSettings=settings;invalidate();}
 public void reloadVisualSettings(){setVisualSettings(VisualSettings.load(getContext()));}
 public void setDoubleTapAction(Runnable action){doubleTap=action;}
 public boolean onTouchEvent(MotionEvent e){return gestures.onTouchEvent(e) || super.onTouchEvent(e);}
 public boolean performClick(){super.performClick();return true;}
 public void attach(PlayerService service,boolean playing){int id=service.audioSession();boolean changed=owner!=service || session!=id;if(changed){release();owner=service;session=id;}if(active!=playing){if(playing)owner.retainAnalysis(analysisOwner);else owner.releaseAnalysis(analysisOwner);reaction.reset();hasFrame=false;active=playing;changed=true;}if(changed)invalidate();}
 public void retry(){if(owner!=null)owner.releaseAnalysis();reaction.reset();hasFrame=false;invalidate();}
 public void release(){boolean changed=owner!=null || active;if(owner!=null)owner.releaseAnalysis(analysisOwner);owner=null;session=-1;active=false;hasFrame=false;reaction.reset();lastCapture=0;if(changed)invalidate();}
 protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);long now=SystemClock.uptimeMillis();if(active && owner!=null && now-lastCapture>=30){lastCapture=now;hasFrame=owner.analysisFrame(reaction,now);message=owner.analysisStatus();}reaction.copy(audio,now,active);float w=getWidth(),h=getHeight();if(w<=0 || h<=0)return;String label=!showLabel?"":!active?"双击特效 · 横屏专注":!hasFrame?message:audio.signal?"原始音频 · 低音 "+(int)(audio.bass*100)+"%":"原始音频 · 安静片段";art.draw(canvas,w,h,getResources().getDisplayMetrics().density,getResources().getDisplayMetrics().scaledDensity,audio,active,accent,label,visualSettings);if(active && isShown())postInvalidateDelayed(33);}
}
