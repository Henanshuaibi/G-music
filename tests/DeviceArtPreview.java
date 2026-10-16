package cn.pulse.player;
import android.graphics.*;
import java.io.*;
import java.util.*;

/** Checks the production graphics without a UI window or the app-initialized label font. */
public final class DeviceArtPreview {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){try{File folder=new File(args[0]);folder.mkdirs();run(folder);System.exit(0);}catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 static Bitmap render(int w,int h,float density,AudioReaction.Frame audio,boolean active,String label){
  Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);new ReactionArt().draw(canvas,w,h,density,density,audio,active,0xffeeeeee,label);return image;
 }
 static void save(Bitmap image,File folder,String name)throws Exception{try(OutputStream out=new FileOutputStream(new File(folder,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}
 static Rect bounds(Bitmap image){
  int w=image.getWidth(),h=image.getHeight(),minX=w,minY=h,maxX=-1,maxY=-1;int[] row=new int[w];
  for(int y=0;y<h;y++){image.getPixels(row,0,w,0,y,w,1);for(int x=0;x<w;x++)if((row[x]&255)>25){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
  return new Rect(minX,minY,maxX+1,maxY+1);
 }
 static Rect regionBounds(Bitmap image,int left,int right){
  Bitmap region=Bitmap.createBitmap(image,left,0,right-left,image.getHeight());Rect result=bounds(region);region.recycle();return result;
 }
 static void run(File folder)throws Exception{
  AudioReaction.Frame audio=new AudioReaction.Frame();AudioReaction detector=new AudioReaction();float[] pcm=new float[1024];PcmSignal silent=new PcmSignal(pcm);detector.accept(silent.wave,silent.fft,44100,1000);
  for(int i=0;i<pcm.length;i++)pcm[i]=(float)(.65*Math.sin(2*Math.PI*3*i/1024)+.18*Math.sin(2*Math.PI*16*i/1024));PcmSignal signal=new PcmSignal(pcm);detector.accept(signal.wave,signal.fft,44100,1033);detector.copy(audio,1033,true);
  check(audio.signal && audio.bass>.5f,"source PCM drives the circle");
  Bitmap peak=render(2400,1080,3,audio,true,"");save(peak,folder,"landscape-beat.png");peak.recycle();
  Bitmap portrait=render(1080,1050,3,audio,true,"");save(portrait,folder,"portrait-beat.png");portrait.recycle();
  for(int i=1;i<=7;i++)detector.accept(signal.wave,signal.fft,44100,1033+i*33);detector.copy(audio,1264,true);
  Bitmap release=render(2400,1080,3,audio,true,"");save(release,folder,"landscape-steady.png");release.recycle();
  detector.copy(audio,1300,false);Bitmap idle=render(2400,1080,3,audio,false,"");save(idle,folder,"landscape-idle.png");idle.recycle();
  // Check the full quiet-to-loud range, not only one favorable music frame.
  AudioReaction.Frame ring=new AudioReaction.Frame();ring.signal=true;
  Bitmap calm=render(1200,600,1.5f,ring,true,"");Rect calmBounds=bounds(calm);calm.recycle();ring.bass=ring.energy=1;
  Bitmap hit=render(1200,600,1.5f,ring,true,"");Rect hitBounds=bounds(hit);hit.recycle();
  check(hitBounds.height()>calmBounds.height()*1.02f && hitBounds.height()<calmBounds.height()*1.05f,"large circle moves gently across the full audio range");
  // Only the waveform reaches this left-side region: check its vertical travel
  // against the original 10%-of-height amplitude, with just a small increase.
  AudioReaction.Frame wave=new AudioReaction.Frame();wave.signal=true;Arrays.fill(wave.wave,1);
  Bitmap up=render(1200,600,1.5f,wave,true,"");Rect upBounds=regionBounds(up,60,160);up.recycle();Arrays.fill(wave.wave,-1);
  Bitmap down=render(1200,600,1.5f,wave,true,"");Rect downBounds=regionBounds(down,60,160);down.recycle();
  float travel=upBounds.exactCenterY()-downBounds.exactCenterY();check(travel>130 && travel<145,"waveform amplitude stays just above the original 120 px peak-to-peak range");
  // Full-scale samples must remain visible and safely inside wide and short surfaces.
  AudioReaction.Frame max=new AudioReaction.Frame();max.signal=true;max.energy=max.bass=1;Arrays.fill(max.bands,1);
  for(int i=0;i<max.wave.length;i++)max.wave[i]=(float)(2*Math.sin(2*Math.PI*i/32));
  for(int[] size:new int[][]{{1200,600},{2400,1080},{1080,1050},{1080,480}}){
   float density=size[0]==1200?1.5f:3;Bitmap full=render(size[0],size[1],density,max,true,"");Rect b=bounds(full);
   check(b.width()>=size[0]*.94f && b.height()>=size[1]*.82f,"slightly smaller circle keeps the composition broad at "+size[0]+"x"+size[1]+": "+b);
   check(b.left>0 && b.top>0 && b.right<size[0] && b.bottom<size[1],"strong peaks and circle are not clipped");full.recycle();
  }
  Bitmap stopped=render(1200,600,1.5f,max,false,"");Bitmap quiet=render(1200,600,1.5f,new AudioReaction.Frame(),false,"");check(stopped.sameAs(quiet),"pause cannot animate from a stale audio frame");stopped.recycle();quiet.recycle();
  System.out.println("PASS: production Canvas renders, gentle circle response over the full audio range and restrained waveform amplitude, nearly full-surface extent at four sizes, bounded loud peaks and calm pause; PNGs in "+folder);
 }
}
