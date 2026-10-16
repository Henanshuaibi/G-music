package cn.pulse.player;

/** Turns captured playback PCM/FFT into visual envelopes and bass onset events. */
public final class AudioReaction {
 public static final int BANDS=48, SAMPLES=128;
 private final float[] waveform=new float[SAMPLES],spectrum=new float[BANDS];
 private float energy,bass,averageBass=.08f,previousBass;
 private long lastFrame,lastBeat=Long.MIN_VALUE/2;
 private int beatCount;
 private boolean signal;
 public static final class Frame {
  public final float[] wave=new float[SAMPLES],bands=new float[BANDS];
  public float energy,bass;public int beatCount;public boolean signal;
 }
 private static float clamp(float f){return Math.max(0,Math.min(1,f));}
 public synchronized void accept(byte[] pcm,byte[] fft,int rate,long now){
  if(pcm==null || fft==null || pcm.length<16 || fft.length<16 || rate<=0)return;
  float dt=lastFrame==0?.033f:Math.max(.005f,Math.min(.1f,(now-lastFrame)/1000f));
  double sum=0;for(int i=0;i<pcm.length;i++){float value=((pcm[i]&255)-128)/128f;sum+=value*value;}
  float rms=(float)Math.sqrt(sum/pcm.length),gain=Math.min(3f,.45f/Math.max(.15f,rms));
  for(int i=0;i<SAMPLES;i++)waveform[i]=((pcm[i*(pcm.length-1)/(SAMPLES-1)]&255)-128)/128f*gain;
  int bins=fft.length/2;float low=0,max=0,nyquist=rate*.5f;
  for(int i=0;i<BANDS;i++){
   float start=55f*(float)Math.pow(Math.min(16000f,nyquist)/55f,i/(float)BANDS);
   float end=55f*(float)Math.pow(Math.min(16000f,nyquist)/55f,(i+1f)/BANDS);
   int first=Math.max(1,Math.min(bins-1,(int)Math.floor(start*fft.length/rate)));
   int stop=Math.max(first+1,Math.min(bins,(int)Math.ceil(end*fft.length/rate)));float peak=0;
   for(int k=first;k<stop;k++)peak=Math.max(peak,(float)Math.hypot(fft[2*k],fft[2*k+1]));
   max=Math.max(max,peak);float value=clamp((float)(Math.log1p(peak)/Math.log(65)));
   spectrum[i]+=(value-spectrum[i])*(value>spectrum[i]?.72f:Math.min(1,dt*9));
  }
  // 45–220 Hz captures kick drums and bass at normal media sample rates.
  for(int k=Math.max(1,(int)(45f*fft.length/rate));k<bins && k*rate/(float)fft.length<=220;k++)low=Math.max(low,(float)Math.hypot(fft[2*k],fft[2*k+1]));
  float strength=clamp((float)(Math.log1p(low)/Math.log(65)));
  signal=rms>.006f || max>1.5f;
  if(signal && strength>.12f && strength>averageBass*1.25f+.035f && strength-previousBass>.07f && now-lastBeat>=180){beatCount++;lastBeat=now;}
  previousBass=strength;averageBass+=(strength-averageBass)*Math.min(1,dt*1.5f);
  float target=signal?clamp(rms*2.8f):0;
  energy+=(target-energy)*(target>energy?.65f:Math.min(1,dt*7));
  bass+=(strength-bass)*(strength>bass?.75f:Math.min(1,dt*8));lastFrame=now;
 }
 public synchronized void copy(Frame out,long now,boolean playing){
  boolean fresh=playing && lastFrame>0 && now-lastFrame<300;
  out.signal=fresh && signal;out.energy=fresh?energy:0;out.bass=fresh?bass:0;out.beatCount=beatCount;
  for(int i=0;i<SAMPLES;i++)out.wave[i]=fresh?waveform[i]:0;
  for(int i=0;i<BANDS;i++)out.bands[i]=fresh?spectrum[i]:0;
 }
 public synchronized void reset(){
  java.util.Arrays.fill(waveform,0);java.util.Arrays.fill(spectrum,0);
  energy=bass=previousBass=0;averageBass=.08f;lastFrame=0;lastBeat=Long.MIN_VALUE/2;beatCount=0;signal=false;
 }
}
