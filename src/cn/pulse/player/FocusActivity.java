package cn.pulse.player;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
/** Separate landscape surface keeps the queue and media service alive across rotation. */
public final class FocusActivity extends Activity {
 private PulseView visual;private PlayerService service;private boolean bound,resumed;
 private final Runnable playerChanged=()->{if(resumed && service!=null)visual.attach(service,service.playing());};
 private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){if(service!=null)service.removeListener(playerChanged);service=((PlayerService.LocalBinder)b).get();if(resumed)service.addListener(playerChanged);playerChanged.run();}public void onServiceDisconnected(ComponentName n){if(service!=null)service.removeListener(playerChanged);service=null;visual.release();}};
 public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=getWindow().getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(p);}visual=new PulseView(this);visual.setBackgroundColor(Color.BLACK);visual.showLabel=false;ThemePack pack=ThemePack.active(this);if(pack!=null)visual.accent=pack.accent;visual.setDoubleTapAction(this::finish);setContentView(visual);hideBars();bound=bindService(new Intent(this,PlayerService.class),connection,BIND_AUTO_CREATE);}
 private void hideBars(){if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
 public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideBars();}
 protected void onResume(){super.onResume();visual.reloadVisualSettings();resumed=true;hideBars();if(service!=null)service.addListener(playerChanged);playerChanged.run();}
 protected void onPause(){resumed=false;if(service!=null)service.removeListener(playerChanged);visual.release();super.onPause();}
 protected void onDestroy(){resumed=false;if(service!=null)service.removeListener(playerChanged);visual.release();if(bound)unbindService(connection);super.onDestroy();}
}
