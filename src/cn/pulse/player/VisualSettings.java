package cn.pulse.player;
import android.content.Context;
import android.content.SharedPreferences;
/** Percentages relative to the accepted 0.24 visuals. Shared by portrait and landscape. */
public final class VisualSettings {
 public static final int CIRCLE_MIN=70,CIRCLE_MAX=115,MOTION_MIN=0,MOTION_MAX=200,BARS_MIN=50,BARS_MAX=180;
 public static final VisualSettings DEFAULT=new VisualSettings(100,100,100);
 public final int circle,motion,bars;
 public VisualSettings(int circle,int motion,int bars){this.circle=clamp(circle,CIRCLE_MIN,CIRCLE_MAX);this.motion=clamp(motion,MOTION_MIN,MOTION_MAX);this.bars=clamp(bars,BARS_MIN,BARS_MAX);}
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 public float circleScale(){return circle/100f;}public float motionScale(){return motion/100f;}public float barScale(){return bars/100f;}
 private static int read(SharedPreferences prefs,String key){try{return prefs.getInt(key,100);}catch(ClassCastException e){return 100;}}
 public static VisualSettings load(Context context){SharedPreferences prefs=context.getSharedPreferences("visuals",0);return new VisualSettings(read(prefs,"circle"),read(prefs,"motion"),read(prefs,"bars"));}
 public void save(Context context){context.getSharedPreferences("visuals",0).edit().putInt("circle",circle).putInt("motion",motion).putInt("bars",bars).apply();}
}
