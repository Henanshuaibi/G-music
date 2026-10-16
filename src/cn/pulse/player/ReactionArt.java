package cn.pulse.player;
import android.graphics.*;
/** Wide presentation with motion kept just above the original subtle response. */
public final class ReactionArt {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private int accent;
 private void color(int alpha){paint.setColor((Math.max(0,Math.min(255,alpha))<<24)|(accent&0xffffff));}
 private static float clamp(float value){return Math.max(0,Math.min(1,value));}
 public void draw(Canvas canvas,float w,float h,float d,float scaled,AudioReaction.Frame audio,boolean active,int accent,String label){draw(canvas,w,h,d,scaled,audio,active,accent,label,VisualSettings.DEFAULT);}
 public void draw(Canvas canvas,float w,float h,float d,float scaled,AudioReaction.Frame audio,boolean active,int accent,String label,VisualSettings settings){
  if(w<=0 || h<=0)return;
  this.accent=accent;int checkpoint=canvas.save();canvas.clipRect(0,0,w,h);
  float inset=Math.max(6*d,Math.min(w,h)*.025f),labelSpace=label.isEmpty()?0:Math.max(24*d,18*scaled);
  float top=inset,bottom=Math.max(top+1,h-inset-labelSpace),height=bottom-top,width=Math.max(1,w-2*inset),cx=w*.5f,cy=top+height*.48f;
  boolean reacting=active && audio.signal;
  // Keep the larger layout separate from motion. Use the original geometry
  // as a displacement reference so a bigger circle does not amplify every beat.
  float originalRadius=Math.min(w*.24f,h*.24f),bass=reacting?clamp(audio.bass):0;
  float radius=Math.min(width*.47f,height*.44f)*.84f*settings.circleScale()+originalRadius*.050f*bass*settings.motionScale();
  radius=Math.min(radius,Math.max(0,Math.min(width*.5f,Math.min(cy-top,bottom-cy))-d*1.1f));
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d*1.1f);color(active?120:55);canvas.drawCircle(cx,cy,radius,paint);
  float waveHeight=Math.min(originalRadius*.60f,h*.10f)*1.15f*settings.motionScale();
  waveHeight=Math.min(waveHeight,Math.max(0,Math.min(cy-top,bottom-cy)-d*1.3f));
  path.reset();for(int i=0;i<audio.wave.length;i++){
   float x=inset+width*i/(audio.wave.length-1),sample=reacting?Math.max(-1,Math.min(1,audio.wave[i])):0,y=cy+sample*waveHeight;
   if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
  }
  paint.setStrokeWidth(d*1.3f);color(active?220:60);canvas.drawPath(path,paint);
  float step=width/audio.bands.length;paint.setStyle(Paint.Style.FILL);
  if(reacting)for(int i=0;i<audio.bands.length;i++){
   float raw=clamp(audio.bands[i]);if(raw<.015f)continue;float value=raw;color((int)(60+value*130));
   float bar=Math.max(d*.7f,step*.45f),left=inset+step*i+(step-bar)/2;
   float barHeight=Math.min(value*h*.140f*settings.barScale(),height*.40f);
   canvas.drawRoundRect(left,bottom-barHeight,left+bar,bottom,d*.5f,d*.5f,paint);
  }
  if(!label.isEmpty()){paint.setColor(0xff999999);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(11*scaled);canvas.drawText(label,cx,h-Math.max(6*d,paint.getFontMetrics().descent),paint);}
  canvas.restoreToCount(checkpoint);
 }
}
