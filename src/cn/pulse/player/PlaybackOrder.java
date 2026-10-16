package cn.pulse.player;
import java.util.Random;
/** Queue-only modes. Repeat-one applies to completion, never traps manual skipping. */
public final class PlaybackOrder {
 public static final int LIST=0,ONE=1,SHUFFLE=2;
 public static String label(int mode){return mode==ONE?"单曲循环":mode==SHUFFLE?"随机播放":"顺序循环";}
 public static int nextMode(int mode){return (mode+1)%3;}
 public static int choose(int size,int current,int mode,int step,boolean completed,Random random){
  if(size==0)return -1;if(current<0 || current>=size)return 0;
  if(completed && mode==ONE)return current;
  if(mode==SHUFFLE && size>1){int n=random.nextInt(size-1);return n>=current?n+1:n;}
  return (current+step%size+size)%size;
 }
}
