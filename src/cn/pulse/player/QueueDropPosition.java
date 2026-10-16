package cn.pulse.player;
/** Insertion slots use each visible row's midpoint, including partially clipped rows. */
public final class QueueDropPosition {
 private QueueDropPosition(){}
 public static int slot(float y,int first,int total,int[] tops,int[] bottoms){
  if(total<=0 || tops.length==0 || tops.length!=bottoms.length)return -1;
  for(int i=0;i<tops.length;i++)if(y<(tops[i]+bottoms[i])/2f)return Math.max(0,Math.min(total,first+i));
  return Math.max(0,Math.min(total,first+tops.length));
 }
 public static int scroll(float y,int height,int edge,int maxStep){
  if(height<=0 || edge<=0)return 0;
  if(y<edge)return -Math.max(1,Math.round(maxStep*Math.min(1,(edge-y)/edge)));
  if(y>height-edge)return Math.max(1,Math.round(maxStep*Math.min(1,(y-height+edge)/edge)));
  return 0;
 }
}
