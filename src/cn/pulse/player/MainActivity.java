package cn.pulse.player;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public class MainActivity extends Activity {
 private PlayerService service;private boolean bound=false,resumed=false,dragging=false;
 private LinearLayout playbackPanel,visualPanel;
 private final SeekBar[] visualSliders=new SeekBar[3];private final TextView[] visualLabels=new TextView[3];
 private Button visualPlay;private VisualSettings visualSettings;private boolean visualDirty;
 private final Runnable saveVisual=()->{if(visualDirty && visualSettings!=null){visualSettings.save(this);visualDirty=false;}};
 private LinearLayout root;private FrameLayout screen;private PlayerService.Track pendingDownload;
 private AlertDialog themeInputDialog,importDialog;
 private SongImportTask songImport;private ArrayList<String> pendingImport;
 private final ExecutorService imports=Executors.newSingleThreadExecutor();
 private PulseView pulse;private TextView title,artist,state,time;private Button play,like,order;private SeekBar seek;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService network=Executors.newSingleThreadExecutor();
 private final ExecutorService themes=Executors.newSingleThreadExecutor();
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder b){if(service!=null)service.removeListener(playerChanged);service=((PlayerService.LocalBinder)b).get();if(resumed)service.addListener(playerChanged);refresh();if(pendingImport!=null){ArrayList<String> sources=pendingImport;pendingImport=null;startSongImport(sources);}}
  public void onServiceDisconnected(ComponentName n){if(service!=null)service.removeListener(playerChanged);pulse.release();service=null;handler.removeCallbacks(tick);}
 };
 private final Runnable playerChanged=()->{if(resumed)refresh();};
 private final Runnable tick=new Runnable(){public void run(){if(resumed && service!=null && service.playing()){refreshProgress();handler.postDelayed(this,250);}}};
 private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
 private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);GradientDrawable bg=new GradientDrawable();bg.setColor(0xff111111);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xff303030);b.setBackground(bg);b.setOnClickListener(v->r.run());return b;}
 private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(b,p);}
 public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
  screen=new FrameLayout(this);screen.setBackgroundColor(0xff080808);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(12),dp(18),dp(12));screen.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(screen);getSharedPreferences("extensions",0).edit().remove("petEnabled").apply();
  root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18),bars.top+dp(12),dp(18),bars.bottom+dp(12));}return insets;});
  FrameLayout heading=new FrameLayout(this);root.addView(heading,new LinearLayout.LayoutParams(-1,dp(42)));
  TextView brand=text("G music",17,Color.WHITE);brand.setTypeface(Typeface.create("sans-serif-light",0));brand.setSingleLine(true);brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
  FrameLayout.LayoutParams brandPosition=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);brandPosition.leftMargin=brandPosition.rightMargin=dp(104);heading.addView(brand,brandPosition);
  heading.addView(button("我的喜欢",()->trackListDialog(true)),new FrameLayout.LayoutParams(dp(100),dp(38),Gravity.RIGHT|Gravity.CENTER_VERTICAL));
  state=text("免费 · 无广告 · 声音可视化",11,0xff888888);root.addView(state);
  pulse=new PulseView(this);root.addView(pulse,new LinearLayout.LayoutParams(-1,0,1));
  playbackPanel=new LinearLayout(this);playbackPanel.setOrientation(LinearLayout.VERTICAL);root.addView(playbackPanel,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);playbackPanel.addView(titleRow);title=text("让声音有形",24,Color.WHITE);title.setMaxLines(2);title.setTypeface(Typeface.create("sans-serif-medium",0));titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));like=button("♡",()->{if(service!=null)toggleLike(service.current());});like.setTextSize(24);like.setContentDescription("喜欢当前歌曲");titleRow.addView(like,new LinearLayout.LayoutParams(dp(52),dp(44)));
  artist=text("添加你喜欢的音乐",12,0xff999999);artist.setPadding(0,dp(10),0,dp(12));playbackPanel.addView(artist);
  seek=new SeekBar(this);seek.setProgressTintList(ColorStateList.valueOf(Color.WHITE));seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));playbackPanel.addView(seek,new LinearLayout.LayoutParams(-1,dp(34)));
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(service!=null)service.seek(s.getProgress());dragging=false;refreshProgress();}public void onProgressChanged(SeekBar s,int p,boolean user){if(user && service!=null)time.setText(clock(p)+"  /  "+clock(service.duration()));}});
  FrameLayout timeline=new FrameLayout(this);playbackPanel.addView(timeline,new LinearLayout.LayoutParams(-1,dp(34)));
  time=text("0:00  /  0:00",11,0xff888888);timeline.addView(time,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
  order=button("顺序循环",()->{if(service!=null){service.cyclePlaybackMode();refresh();}});order.setTextSize(11);timeline.addView(order,new FrameLayout.LayoutParams(dp(94),dp(30),Gravity.RIGHT|Gravity.CENTER_VERTICAL));
  LinearLayout controls=new LinearLayout(this);playbackPanel.addView(controls);addButton(controls,button("上一首",()->{if(service!=null)service.next(-1);}));play=button("播放",()->{if(service!=null){if(service.tracks.isEmpty())toast("先导入歌曲或添加音频链接");else service.toggle();}});addButton(controls,play);addButton(controls,button("下一首",()->{if(service!=null)service.next(1);}));
  LinearLayout library=new LinearLayout(this);playbackPanel.addView(library);addButton(library,button("找歌",this::searchDialog));addButton(library,button("播放列表",this::queueDialog));addButton(library,button("更多",this::moreMenu));
  pulse.setDoubleTapAction(()->startActivity(new Intent(this,FocusActivity.class)));applyTheme();themes.execute(()->ThemePack.removeLegacyPlugins(this));
  bound=bindService(new Intent(this,PlayerService.class),connection,BIND_AUTO_CREATE);
  if(saved!=null && saved.getBoolean("visualAdjusting"))root.post(this::showVisualSettings);
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
 }
 public void onRequestPermissionsResult(int request,String[] p,int[] results){super.onRequestPermissionsResult(request,p,results);if(request==12){PlayerService.Track track=pendingDownload;pendingDownload=null;if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED && track!=null)TrackDownloads.start(this,track);else toast("未获得保存权限，下载未开始");}refresh();}
 private void moreMenu(){String[] options={"导入本地歌曲","导入播放链接","主题","视觉调节","数据备份与恢复","音频分析信息"};Runnable[] actions={this::importSongs,this::linkDialog,this::extensions,this::showVisualSettings,this::backupMenu,this::analysisInfo};new AlertDialog.Builder(this).setTitle("更多").setItems(options,(d,n)->actions[n].run()).setNegativeButton("关闭",null).show();}
 private void analysisInfo(){new AlertDialog.Builder(this).setTitle("音频分析").setMessage("双击特效进入纯特效横屏，再双击或返回退出。系统音量为零仍可显示节奏。\n"+(service==null?"等待播放":service.analysisStatus())).setPositiveButton("重新分析",(a,b)->pulse.retry()).setNegativeButton("关闭",null).show();}
 private void flushVisualSettings(){handler.removeCallbacks(saveVisual);saveVisual.run();}
 private void showVisualSettings(){
  if(isFinishing() || isDestroyed())return;flushVisualSettings();visualSettings=VisualSettings.load(this);
  if(visualPanel==null){
   visualPanel=new LinearLayout(this);visualPanel.setOrientation(LinearLayout.VERTICAL);root.addView(visualPanel);
   TextView hint=text("视觉调节 · 100% 为默认效果",12,0xffaaaaaa);hint.setPadding(0,dp(4),0,dp(6));visualPanel.addView(hint);
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);visualPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout sliders=new LinearLayout(this);sliders.setOrientation(LinearLayout.VERTICAL);scroll.addView(sliders);
   addVisualSlider(sliders,0,VisualSettings.CIRCLE_MIN,VisualSettings.CIRCLE_MAX);
   addVisualSlider(sliders,1,VisualSettings.MOTION_MIN,VisualSettings.MOTION_MAX);
   addVisualSlider(sliders,2,VisualSettings.BARS_MIN,VisualSettings.BARS_MAX);
   LinearLayout actions=new LinearLayout(this);visualPanel.addView(actions);
   visualPlay=button("播放",()->{if(service!=null){if(service.tracks.isEmpty())toast("先导入歌曲，再调节动态效果");else service.toggle();}refresh();});addButton(actions,visualPlay);
   addButton(actions,button("恢复默认",()->{visualSettings=VisualSettings.DEFAULT;syncVisualSliders();visualDirty=true;flushVisualSettings();}));
   addButton(actions,button("完成",this::closeVisualSettings));
  }
  int height=Math.min(dp(330),Math.max(dp(150),root.getHeight()/2));visualPanel.setLayoutParams(new LinearLayout.LayoutParams(-1,height));
  syncVisualSliders();playbackPanel.setVisibility(View.GONE);visualPanel.setVisibility(View.VISIBLE);refresh();
 }
 private void addVisualSlider(LinearLayout parent,int index,int min,int max){
  TextView label=text("",13,Color.WHITE);label.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);label.setPadding(dp(8),dp(2),0,0);parent.addView(label);visualLabels[index]=label;
  SeekBar slider=new SeekBar(this);slider.setMin(min);slider.setMax(max);slider.setProgress(100);slider.setKeyProgressIncrement(5);slider.setProgressTintList(ColorStateList.valueOf(Color.WHITE));slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));parent.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));visualSliders[index]=slider;
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){flushVisualSettings();}public void onProgressChanged(SeekBar bar,int value,boolean user){if(!user)return;visualSettings=new VisualSettings(visualSliders[0].getProgress(),visualSliders[1].getProgress(),visualSliders[2].getProgress());updateVisualLabels();pulse.setVisualSettings(visualSettings);visualDirty=true;handler.removeCallbacks(saveVisual);handler.postDelayed(saveVisual,250);}});
 }
 private void updateVisualLabels(){String[] names={"圆圈大小","跳动幅度（圆圈 / 波形）","频谱条高度"};int[] values={visualSettings.circle,visualSettings.motion,visualSettings.bars};for(int i=0;i<3;i++){visualLabels[i].setText(names[i]+"  "+values[i]+"%");visualSliders[i].setContentDescription(names[i]+"，"+values[i]+"%");}}
 private void syncVisualSliders(){visualSliders[0].setProgress(visualSettings.circle);visualSliders[1].setProgress(visualSettings.motion);visualSliders[2].setProgress(visualSettings.bars);updateVisualLabels();pulse.setVisualSettings(visualSettings);}
 private boolean visualSettingsOpen(){return visualPanel!=null && visualPanel.getVisibility()==View.VISIBLE;}
 private void closeVisualSettings(){flushVisualSettings();if(visualPanel!=null)visualPanel.setVisibility(View.GONE);playbackPanel.setVisibility(View.VISIBLE);}
 public void onBackPressed(){if(visualSettingsOpen())closeVisualSettings();else super.onBackPressed();}
 protected void onSaveInstanceState(Bundle state){flushVisualSettings();state.putBoolean("visualAdjusting",visualSettingsOpen());super.onSaveInstanceState(state);}
 private void download(PlayerService.Track track){if(Build.VERSION.SDK_INT<29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){pendingDownload=track;requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},12);return;}TrackDownloads.start(this,track);}
 private void applyTheme(){
  ThemePack pack=ThemePack.active(this);screen.setBackgroundColor(pack==null?0xff080808:pack.backgroundColor);pulse.accent=pack==null?0xffeeeeee:pack.accent;pulse.invalidate();
  if(pack!=null)try{android.graphics.drawable.Drawable wallpaper=pack.background();if(wallpaper!=null)screen.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(pack.backgroundColor),wallpaper}));}catch(Exception e){toast("壁纸读取失败，已使用主题底色");}
 }
 private void extensions(){
  ThemePack active=ThemePack.active(this);String[] options={"粘贴主题代码","复制当前主题代码","管理主题 · 右划删除","恢复默认主题"};
  new AlertDialog.Builder(this).setTitle("主题 · "+(active==null?"默认":active.name)).setItems(options,(d,n)->{if(n==0)pasteTheme();else if(n==1)copyTheme();else if(n==2)manageThemes();else{ThemePack.select(this,null);applyTheme();}}).setNegativeButton("关闭",null).show();
 }
 private void manageThemes(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),0,dp(16),dp(8));TextView hint=text("点击切换 · 向右划出删除按钮",12,0xffaaaaaa);hint.setPadding(0,dp(8),0,dp(8));box.addView(hint);
  Button defaultTheme=button("使用默认主题",()->{});box.addView(defaultTheme,new LinearLayout.LayoutParams(-1,dp(44)));
  FrameLayout results=new FrameLayout(this);box.addView(results,new LinearLayout.LayoutParams(-1,Math.max(dp(140),Math.min(dp(360),getResources().getDisplayMetrics().heightPixels/2))));ListView list=new ListView(this);results.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=text("还没有保存主题",14,0xffaaaaaa);results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
  ArrayList<ThemePack> packs=ThemePack.list(this);final SwipeTrackRow[] open=new SwipeTrackRow[1];final BaseAdapter[] adapter=new BaseAdapter[1];Runnable reload=()->{if(open[0]!=null){open[0].close();open[0]=null;}packs.clear();packs.addAll(ThemePack.list(this));adapter[0].notifyDataSetChanged();};
  adapter[0]=new BaseAdapter(){public int getCount(){return packs.size();}public Object getItem(int n){return packs.get(n);}public long getItemId(int n){return n;}
   public View getView(int n,View reused,ViewGroup parent){ThemePack pack=packs.get(n);String current=getSharedPreferences("extensions",0).getString("active","");boolean selected=pack.directory.getName().equals(current);
    Runnable select=()->{ThemePack.select(MainActivity.this,pack);applyTheme();reload.run();};
    Runnable remove=()->{try{if(pack.directory.getName().equals(getSharedPreferences("extensions",0).getString("active",""))){ThemePack.select(MainActivity.this,null);applyTheme();}ThemePack.remove(MainActivity.this,pack);toast("已删除："+pack.name);}catch(Exception e){toast(e.getMessage());}reload.run();};
    return new SwipeTrackRow(MainActivity.this,(selected?"✓  ":"")+pack.name,"静态主题",false,"删除",select,null,remove,null,row->{if(open[0]!=null && open[0]!=row)open[0].close();open[0]=row;});
   }
  };list.setAdapter(adapter[0]);defaultTheme.setOnClickListener(v->{ThemePack.select(this,null);applyTheme();reload.run();toast("已切换默认主题");});AlertDialog dialog=new AlertDialog.Builder(this).setTitle("管理主题").setView(box).setNegativeButton("关闭",null).create();dialog.setOnDismissListener(d->{if(open[0]!=null)open[0].close();});dialog.show();
 }

 private void pasteTheme(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),0,dp(16),dp(8));
  EditText input=new EditText(this);input.setHint("在这里粘贴主题代码");input.setTextColor(Color.WHITE);input.setTextSize(12);input.setTypeface(Typeface.MONOSPACE);input.setMinLines(5);input.setMaxLines(10);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ThemeText.MAX_BYTES+1)});box.addView(input,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout actions=new LinearLayout(this);box.addView(actions);addButton(actions,button("粘贴",()->{try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();if(clip==null || clip.getItemCount()==0){toast("剪贴板没有主题代码");return;}CharSequence value=clip.getItemAt(0).coerceToText(this);if(value==null || value.length()>ThemeText.MAX_BYTES){toast("主题代码最多 512 KB，请精简代码或压缩内嵌图片");return;}input.setText(value);}catch(Exception e){toast("无法读取剪贴板，也可以长按输入框粘贴");}}));addButton(actions,button("填入示例",()->{if(input.length()==0)input.setText(ThemeText.example());else new AlertDialog.Builder(this).setMessage("用示例替换当前输入？").setPositiveButton("替换",(d,n)->input.setText(ThemeText.example())).setNegativeButton("取消",null).show();}));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("粘贴主题代码").setMessage("支持静态配色和壁纸，保存后直接应用。").setView(box).setPositiveButton("保存并应用",null).setNegativeButton("取消",null).create();
  themeInputDialog=dialog;dialog.setOnDismissListener(d->{if(themeInputDialog==dialog)themeInputDialog=null;});
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String code=input.getText().toString().trim();if(code.isEmpty()){input.setError("先粘贴主题代码或填入示例");return;}importThemeCode(code);}));dialog.show();
 }
 private void importThemeCode(String code){
  ProgressDialog progress=new ProgressDialog(this);progress.setMessage("正在保存主题…");progress.setCancelable(false);progress.show();themes.execute(()->{try{
   ThemePack pack=ThemeText.install(code,ThemePack.root(this));handler.post(()->{if(isFinishing() || isDestroyed())return;progress.dismiss();ThemePack.select(this,pack);applyTheme();if(themeInputDialog!=null)themeInputDialog.dismiss();toast("已保存并应用："+pack.name);});
  }catch(Exception e){handler.post(()->{if(isFinishing() || isDestroyed())return;progress.dismiss();new AlertDialog.Builder(this).setTitle("主题未导入").setMessage(e.getMessage()==null?"请粘贴有效的静态主题代码":e.getMessage()).setPositiveButton("知道了",null).show();});}});
 }

 private void copyTheme(){
  ThemePack current=ThemePack.active(this);themes.execute(()->{try{String code=ThemeText.encode(current);handler.post(()->{if(isFinishing() || isDestroyed())return;try{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("G music 主题",code));toast("主题代码已复制，可粘贴分享或重新导入");}catch(Exception e){toast("主题内容过大，无法复制为主题代码");}});}catch(Exception e){handler.post(()->{if(!isFinishing() && !isDestroyed())toast(e.getMessage()==null?"主题无法复制":e.getMessage());});}});
 }

 protected void onResume(){super.onResume();pulse.reloadVisualSettings();if(visualSettingsOpen()){visualSettings=VisualSettings.load(this);syncVisualSliders();}resumed=true;if(service!=null)service.addListener(playerChanged);refresh();}
 protected void onPause(){flushVisualSettings();if(service!=null)save();resumed=false;handler.removeCallbacks(tick);if(service!=null)service.removeListener(playerChanged);pulse.release();super.onPause();}
 protected void onDestroy(){flushVisualSettings();resumed=false;handler.removeCallbacks(tick);if(service!=null)service.removeListener(playerChanged);pulse.release();cancelSongImport();imports.shutdownNow();if(themeInputDialog!=null)themeInputDialog.dismiss();if(service!=null)save();if(bound)unbindService(connection);network.shutdownNow();themes.shutdownNow();super.onDestroy();}

 private void refresh(){
  handler.removeCallbacks(tick);if(service==null)return;PlayerService.Track t=service.current();
  setText(title,t==null?"让声音有形":t.title);setText(artist,t==null?"添加你喜欢的音乐":t.artist);
  setText(state,service.status);String playLabel=service.playRequested()?"暂停":"播放";setText(play,playLabel);if(visualPlay!=null)setText(visualPlay,playLabel);
  String mode=PlaybackOrder.label(service.playbackMode());setText(order,mode);order.setContentDescription("播放模式："+mode+"，点击切换");
  boolean favorite=service.isFavorite(t);setText(like,favorite?"♥":"♡");like.setTextColor(favorite?0xffff748f:Color.WHITE);like.setEnabled(t!=null);like.setContentDescription(favorite?"取消喜欢当前歌曲":"喜欢当前歌曲");
  refreshProgress();if(resumed){boolean playing=service.playing();pulse.attach(service,playing);if(playing)handler.postDelayed(tick,250);}
 }
 private static void setText(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
 private void refreshProgress(){
  if(service==null || dragging)return;int duration=service.duration(),position=service.position(),max=Math.max(1,duration);
  if(seek.getMax()!=max)seek.setMax(max);if(seek.getProgress()!=position)seek.setProgress(position);setText(time,clock(position)+"  /  "+clock(duration));
 }
 private String clock(int ms){return String.format(Locale.ROOT,"%d:%02d",ms/60000,ms/1000%60);}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 private void importSongs(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,1);}
 protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==4 || req==5){if(result==RESULT_OK && data!=null && data.getData()!=null){if(req==4)exportBackup(data.getData());else importBackup(data.getData());}return;}if(req!=1 || result!=RESULT_OK || data==null)return;
  ArrayList<String> sources=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri uri=data.getClipData().getItemAt(i).getUri();if(uri!=null)sources.add(uri.toString());}}else if(data.getData()!=null)sources.add(data.getData().toString());
  if(service==null){pendingImport=sources;toast("正在连接播放器，随后开始导入");}else startSongImport(sources);
 }
 private void cancelSongImport(){if(songImport!=null){songImport.cancel();songImport=null;}if(importDialog!=null){importDialog.dismiss();importDialog=null;}}
 private void startSongImport(ArrayList<String> sources){
  if(sources.isEmpty() || isFinishing() || isDestroyed())return;cancelSongImport();
  LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(16),dp(24),dp(12));
  TextView progressText=text("正在读取歌曲信息 0 / "+sources.size(),14,Color.WHITE);content.addView(progressText);
  ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(sources.size());content.addView(progress,new LinearLayout.LayoutParams(-1,dp(28)));
  importDialog=new AlertDialog.Builder(this).setTitle("导入本地歌曲").setView(content).setNegativeButton("取消",(d,w)->cancelSongImport()).create();importDialog.setCanceledOnTouchOutside(false);importDialog.setOnCancelListener(d->cancelSongImport());importDialog.show();
  SongImportTask task=new SongImportTask();songImport=task;
  task.start(sources,new LocalSongReader(this),imports,command->handler.post(command),new SongImportTask.Listener(){
   public void progress(int done,int total){if(songImport!=task || isFinishing() || isDestroyed())return;progress.setProgress(done);progressText.setText("正在读取歌曲信息 "+done+" / "+total);}
   public void complete(List<PlayerService.Track> songs,int failed){
    if(songImport!=task || isFinishing() || isDestroyed())return;songImport=null;if(importDialog!=null){importDialog.dismiss();importDialog=null;}
    if(service==null){toast("播放器已断开，请重新导入");return;}
    int start=service.tracks.size();service.add(songs);if(service.current()==null && !songs.isEmpty())service.play(start);refresh();toast("已导入 "+songs.size()+" 首歌曲"+(failed>0?"，跳过 "+failed+" 个无法读取的文件":""));
   }
  });
 }
 private void toggleLike(PlayerService.Track track){toggleLike(track,null);}
 private void toggleLike(PlayerService.Track track,Runnable changed){
  if(service==null || track==null){toast("请先选择一首歌");return;}if(!service.canFavorite(track)){toast("请先播放并展开合集，再喜欢其中的单曲");return;}
  Runnable toggle=()->{if(service==null)return;boolean added=service.toggleFavorite(track);toast(added?"已加入我的喜欢":"已取消喜欢");refresh();if(changed!=null)changed.run();};
  PlayerService.Track duplicate=service.isFavorite(track)?null:service.duplicateFavorite(track);if(duplicate!=null){new AlertDialog.Builder(this).setTitle("可能已喜欢这首歌").setMessage("已收藏："+duplicate.title+"\n歌名、版本和时长相近，来源不同。保留原收藏，还是再添加这一版？").setPositiveButton("仍然添加",(d,n)->toggle.run()).setNegativeButton("不重复添加",null).show();}else toggle.run();
 }
 private void reviewDuplicates(){if(service==null)return;ArrayList<PlayerService.Track> duplicates=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(PlayerService.Track[] pair:SongIdentity.duplicatePairs(service.favorites())){duplicates.add(pair[0]);labels.add(pair[0].title+"\n相似收藏："+pair[1].title);}if(duplicates.isEmpty()){toast("没有发现标题、歌手和时长相近的重复收藏");return;}new AlertDialog.Builder(this).setTitle("疑似重复 · "+duplicates.size()+" 首").setItems(labels.toArray(new String[0]),(d,n)->{PlayerService.Track track=duplicates.get(n);new AlertDialog.Builder(this).setTitle("取消这条收藏？").setMessage(track.title+"\n只取消这一来源的喜欢，保留另一条收藏。").setPositiveButton("取消这条喜欢",(a,k)->{if(service!=null && service.isFavorite(track))service.toggleFavorite(track);refresh();reviewDuplicates();}).setNegativeButton("保留",null).show();}).setNegativeButton("关闭",null).show();}
 private void backupMenu(){new AlertDialog.Builder(this).setTitle("数据备份与恢复").setItems(new String[]{"导出收藏、列表与视觉设置","从备份合并恢复","覆盖更新说明"},(d,n)->{if(n==0){flushVisualSettings();if(service!=null)save();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"G-music-backup-"+new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.ROOT).format(new Date())+".json");startActivityForResult(intent,4);}else if(n==1)startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),5);else new AlertDialog.Builder(this).setTitle("更新无需卸载").setMessage("后续版本保持同一包名和签名，直接覆盖安装可保留数据。若系统提示冲突，先不要卸载。\n导出的 JSON 保存收藏、播放列表、播放模式和视觉设置，不包含音频及主题文件。请保存到 Download 等公共目录；卸载后可合并恢复，本地歌曲需重新导入授权。").setPositiveButton("知道了",null).show();}).setNegativeButton("关闭",null).show();}
 private void exportBackup(Uri uri){network.execute(()->{try{String snapshot=LibraryBackup.capture(this);try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("无法写入文件");output.write(snapshot.getBytes("UTF-8"));output.flush();}handler.post(()->{if(!isFinishing() && !isDestroyed())toast("收藏、播放列表、模式和视觉设置已备份");});}catch(Exception e){handler.post(()->{if(!isFinishing() && !isDestroyed())toast("备份失败，请重新选择保存位置");});}});}
 private void importBackup(Uri uri){network.execute(()->{try{LibraryBackup.Data backup;try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("无法读取文件");backup=LibraryBackup.read(input);}handler.post(()->{if(isFinishing() || isDestroyed())return;new AlertDialog.Builder(this).setTitle("合并恢复备份").setMessage("备份包含 "+backup.favorites.size()+" 条收藏、"+backup.queue.size()+" 首列表歌曲。相同来源不会重复添加，当前收藏和播放会保留。"+(backup.visuals==null?"\n旧备份没有视觉设置，将保留当前效果。":"\n将恢复圆圈大小 "+backup.visuals.circle+"%、跳动幅度 "+backup.visuals.motion+"%、频谱条高度 "+backup.visuals.bars+"%，替换当前视觉设置。")+"\n卸载前导入的本地歌曲可能需要重新授权。").setPositiveButton("合并恢复",(d,n)->{if(service==null){toast("播放器尚未连接，请稍后重试");return;}flushVisualSettings();int[] count=service.restoreLibrary(backup);pulse.reloadVisualSettings();if(visualSettingsOpen()){visualSettings=VisualSettings.load(this);syncVisualSliders();}refresh();toast("新增 "+count[1]+" 条收藏、"+count[0]+" 首列表歌曲"+(backup.visuals==null?"":"，视觉设置已恢复"));}).setNegativeButton("取消",null).show();});}catch(Exception e){handler.post(()->{if(!isFinishing() && !isDestroyed())toast("未恢复："+(e.getMessage()==null?"备份格式不正确":e.getMessage()));});}});}
 private void queueDialog(){trackListDialog(false);}
 private void trackListDialog(boolean favoritesOnly){
  if(service==null)return;
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),0,dp(16),dp(4));
  LinearLayout searchRow=new LinearLayout(this);box.addView(searchRow);EditText query=new EditText(this);query.setHint(favoritesOnly?"搜索我的喜欢":"搜索列表中的歌名或歌手");query.setTextColor(Color.WHITE);query.setSingleLine(true);query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);searchRow.addView(query,new LinearLayout.LayoutParams(0,dp(48),1));
  searchRow.addView(button("清除",()->query.setText("")),new LinearLayout.LayoutParams(dp(64),dp(44)));
  TextView count=text("",12,0xffaaaaaa);count.setPadding(0,dp(8),0,dp(8));box.addView(count);
  FrameLayout results=new FrameLayout(this);int height=Math.max(dp(160),Math.min(dp(460),getResources().getDisplayMetrics().heightPixels/2));box.addView(results,new LinearLayout.LayoutParams(-1,height));
  ReorderListView list=new ReorderListView(this);results.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=text("",14,0xffbbbbbb);results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
  ArrayList<PlayerService.Track> matches=new ArrayList<>();final Runnable[] refilter=new Runnable[1];final AlertDialog[] holder=new AlertDialog[1];final SwipeTrackRow[] openRow=new SwipeTrackRow[1];
  BaseAdapter adapter=new BaseAdapter(){
   public int getCount(){return matches.size();}public Object getItem(int n){return matches.get(n);}public long getItemId(int n){return n;}
   public View getView(int n,View reused,android.view.ViewGroup parent){
    PlayerService.Track track=matches.get(n);boolean current=service!=null && service.current()!=null && (favoritesOnly?service.current().url.equals(track.url):service.current()==track);
    Runnable select=()->{if(service==null)return;if(!favoritesOnly){if(!service.tracks.contains(track)){refilter[0].run();toast("列表已更新，请重新选择歌曲");return;}}holder[0].dismiss();service.playTrack(track);};
    Runnable favorite=()->{if(service==null)return;toggleLike(track,refilter[0]);};
    Runnable remove=()->{if(service==null)return;if(favoritesOnly){if(service.isFavorite(track))service.toggleFavorite(track);toast("已取消喜欢");}else{if(!service.removeTrack(track)){toast("列表已更新");}else toast("已移出播放列表");}refilter[0].run();refresh();};
    return new SwipeTrackRow(MainActivity.this,(current?"▶  ":"")+track.title,track.artist+(track.expectedMs>0?" · "+clock(track.expectedMs):""),service!=null && service.isFavorite(track),favoritesOnly?"取消喜欢":"移出列表",select,favorite,remove,()->download(track),row->{if(openRow[0]!=null && openRow[0]!=row)openRow[0].close();openRow[0]=row;},favoritesOnly?null:new SwipeTrackRow.ReorderActions(){
     public boolean start(SwipeTrackRow row){if(service==null || !service.tracks.contains(track)){refilter[0].run();return false;}if(openRow[0]!=null){openRow[0].prepareForReorder();openRow[0]=null;}return list.startReorder(row,track);}
     public void step(int direction){int at=matches.indexOf(track),to=at+direction;if(service==null || at<0 || to<0 || to>=matches.size())return;int first=list.getFirstVisiblePosition(),top=list.getChildCount()==0?0:list.getChildAt(0).getTop();if(service.moveTrack(track,matches.get(to),direction>0)){refilter[0].run();list.setSelectionFromTop(first,top);list.announceForAccessibility(direction<0?"已上移一位":"已下移一位");refresh();}}
    });
   }
  };list.setAdapter(adapter);
  Runnable filter=()->{
   list.cancelReorder();
   if(openRow[0]!=null){openRow[0].close();openRow[0]=null;}matches.clear();if(service==null){adapter.notifyDataSetChanged();return;}ArrayList<PlayerService.Track> source=favoritesOnly?service.favorites():new ArrayList<>(service.tracks);
   String keyword=query.getText().toString().trim().toLowerCase(Locale.ROOT);String[] words=keyword.isEmpty()?new String[0]:keyword.split("\\s+");long total=0;
   for(PlayerService.Track t:source){total+=t.expectedMs;String haystack=(t.title+" "+t.artist).toLowerCase(Locale.ROOT);boolean match=true;for(String word:words)if(!haystack.contains(word)){match=false;break;}if(match)matches.add(t);}
   count.setText(keyword.isEmpty()?matches.size()+" 首 · "+(favoritesOnly?"长按取消喜欢 · 右划下载":"按住 ☰ 排序 · 停在上下边缘连续滚动"):"找到 "+matches.size()+" / "+source.size()+" 首");
   empty.setText(source.isEmpty()?(favoritesOnly?"长按列表中的歌曲，加入我的喜欢":"播放列表还是空的"):"没有匹配歌曲，换个关键词试试");adapter.notifyDataSetChanged();
  };refilter[0]=filter;
  if(!favoritesOnly)list.enableReorder(matches,pulse.accent,(source,anchor,after)->{
   int first=list.getFirstVisiblePosition(),top=list.getChildCount()==0?0:list.getChildAt(0).getTop();boolean moved=service!=null && service.moveTrack(source,anchor,after);filter.run();list.setSelectionFromTop(first,top);refresh();toast(moved?"已保存播放顺序":"列表已更新，请重新拖动");return moved;
  });
  AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(favoritesOnly?"我的喜欢":"播放列表").setView(box).setNegativeButton("关闭",null);
  if(favoritesOnly)builder.setNeutralButton("检查重复",(d,w)->reviewDuplicates());
  if(!favoritesOnly)builder.setNeutralButton("清空全部",(d,w)->new AlertDialog.Builder(this).setMessage("清空整个播放列表并停止播放？喜欢的歌曲会保留，本地文件不会删除。").setPositiveButton("清空",(a,b)->{if(service!=null){service.clear();save();title.setText("让声音有形");artist.setText("添加你喜欢的音乐");refresh();}}).setNegativeButton("取消",null).show());
  AlertDialog dialog=builder.create();holder[0]=dialog;dialog.setOnDismissListener(d->{list.cancelReorder();list.setDragHost(null);if(openRow[0]!=null)openRow[0].close();});
  query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});
  searchRow.addView(button("搜索",()->{filter.run();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);}),1,new LinearLayout.LayoutParams(dp(58),dp(44)));
  query.setOnEditorActionListener((v,action,event)->{filter.run();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);return true;});
  dialog.setOnShowListener(d->{if(!favoritesOnly)list.setDragHost(dialog.getWindow().getDecorView());dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);filter.run();});dialog.show();
 }
 private void linkDialog(){EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setHint("B站链接 / BV号 / HTTPS音频直链");e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("导入播放链接").setMessage("支持 B站视频链接、b23.tv 分享短链、BV号及 HTTPS 音频直链，可直接粘贴分享文字。").setView(e).setPositiveButton("播放",null).setNegativeButton("取消",null).create();
  d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String raw=e.getText().toString().trim(),source=null,name=null,artist="网络直链";
   java.util.regex.Matcher bv=java.util.regex.Pattern.compile("(?i)\\bBV[0-9a-z]{10}\\b").matcher(raw);
   if(bv.find()){String id="BV"+bv.group().substring(2);java.util.regex.Matcher part=java.util.regex.Pattern.compile("[?&]p=(\\d+)").matcher(raw);source="bili:"+id+(part.find()?"?p="+part.group(1):"");name="B站视频 · "+id;artist="B站";}
   else{java.util.regex.Matcher link=java.util.regex.Pattern.compile("https://[^\\s<>\\x22，。！？）】]+").matcher(raw);
    if(!link.find()){e.setError("请粘贴 HTTPS 链接或完整 BV号");return;}String u=link.group();Uri uri=Uri.parse(u);String host=uri.getHost();if(host==null){e.setError("链接格式不正确");return;}
    if("b23.tv".equalsIgnoreCase(host)){source="bili-link:"+u;name="B站分享视频";artist="B站";}
    else if("bilibili.com".equalsIgnoreCase(host)||host.toLowerCase(Locale.ROOT).endsWith(".bilibili.com")){e.setError("请使用包含 BV号的视频链接或 b23.tv 分享链接");return;}
    else{source=u;name=uri.getLastPathSegment();if(name==null || name.isEmpty())name="网络音频";}
   }
   if(service==null){e.setError("播放器正在初始化，请稍后再试");return;}service.add(Collections.singletonList(new PlayerService.Track(name,artist,source)));save();service.play(service.tracks.size()-1);d.dismiss();}));d.show();
 }
 private void searchDialog(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),0,dp(16),0);EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setHint("歌名或歌手");box.addView(e);LinearLayout artists=new LinearLayout(this);box.addView(artists);addButton(artists,button("周杰伦",()->{e.setText("周杰伦");search("周杰伦");}));addButton(artists,button("李荣浩",()->{e.setText("李荣浩");search("李荣浩");}));addButton(artists,button("徐良",()->{e.setText("徐良");search("徐良 歌曲");}));
  AlertDialog d=new AlertDialog.Builder(this).setTitle("B 站音乐搜索").setMessage("搜索 B 站公开视频，点击结果直接播放声音。可搜原唱、现场或翻唱；多分集合集会加入播放列表并连续播放。").setView(box).setPositiveButton("搜索",null).setNegativeButton("关闭",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String q=e.getText().toString().trim();if(q.isEmpty()){e.setError("输入歌名或歌手");return;}search(q);}));d.show();
 }
 private void search(String q){
  ProgressDialog progress=new ProgressDialog(this);progress.setMessage("正在查找 "+q+"…");progress.setCancelable(true);progress.show();
  network.execute(()->{ArrayList<String> labels=new ArrayList<>(),urls=new ArrayList<>(),names=new ArrayList<>(),authors=new ArrayList<>();String error=null;HttpURLConnection conn=null;
   try{conn=(HttpURLConnection)new URL("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword="+URLEncoder.encode(q,"UTF-8")+"&page=1").openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(12000);conn.setRequestProperty("User-Agent","Mozilla/5.0");conn.setRequestProperty("Referer","https://www.bilibili.com/");
    StringBuilder body=new StringBuilder();try(BufferedReader in=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))){String line;while((line=in.readLine())!=null){body.append(line);if(body.length()>1500000)throw new IOException("Response too large");}}
    JSONObject result=new JSONObject(body.toString());if(result.optInt("code",-1)!=0)throw new IOException("搜索接口暂不可用");
    JSONArray list=result.getJSONObject("data").optJSONArray("result");if(list!=null)for(int i=0;i<list.length();i++){JSONObject t=list.getJSONObject(i);String bv=t.optString("bvid");if(bv.matches("BV[0-9A-Za-z]+")){String name=android.text.Html.fromHtml(t.optString("title"),android.text.Html.FROM_HTML_MODE_LEGACY).toString();String author=t.optString("author");names.add(name);authors.add(author);labels.add(name+" · "+author+" · "+t.optString("duration"));urls.add("bili:"+bv);}}
   }catch(Exception ex){error="搜索暂不可用，请检查网络后重试。你仍可导入本地歌曲或添加音频直链。";}finally{if(conn!=null)conn.disconnect();}
   final String err=error;handler.post(()->{if(isFinishing() || isDestroyed())return;if(progress.isShowing())progress.dismiss();else return;if(err!=null){toast(err);return;}if(labels.isEmpty()){toast("没有找到歌曲，试试其他关键词");return;}
    new AlertDialog.Builder(this).setTitle(q+" · "+labels.size()+" 个结果").setItems(labels.toArray(new String[0]),(d,n)->{if(service!=null){service.add(Collections.singletonList(new PlayerService.Track(names.get(n),"B站 · "+authors.get(n),urls.get(n))));save();service.play(service.tracks.size()-1);}}).setNegativeButton("关闭",null).show();
   });
  });
 }
 private void save(){if(service!=null)service.savePlayback();}
}
