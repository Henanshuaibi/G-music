package cn.pulse.player;
/** Locks a gesture to horizontal reveal or vertical scrolling after touch slop. */
public final class SwipeGesture {
 private final float slop,width;private float start;
 public boolean horizontal,vertical,moved;public float offset;
 public SwipeGesture(float tolerance,float revealWidth){slop=tolerance;width=revealWidth;}
 public void down(float value){start=offset=value;horizontal=vertical=moved=false;}
 public void move(float dx,float dy){
  if(Math.abs(dx)>slop || Math.abs(dy)>slop)moved=true;
  if(!horizontal && !vertical){if(Math.abs(dy)>slop && Math.abs(dy)>=Math.abs(dx))vertical=true;else if(Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.25f && (dx>0 || start>0))horizontal=true;}
  if(horizontal)offset=Math.max(0,Math.min(width,start+dx));
 }
 public boolean reveal(){return horizontal && offset>=width*.45f;}
 public boolean stationary(){return !moved;}
}
