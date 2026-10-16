package cn.pulse.player;
import java.util.Arrays;
public final class AudioReactionCheck {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static byte[] waveform(int amplitude){byte[] data=new byte[1024];for(int i=0;i<data.length;i++)data[i]=(byte)(128+(int)(amplitude*Math.sin(2*Math.PI*3*i/data.length)));return data;}
 public static void main(String[] args){
  AudioReaction detector=new AudioReaction();AudioReaction.Frame frame=new AudioReaction.Frame();
  byte[] silent=waveform(0),fft=new byte[1024];long now=1000;
  for(int i=0;i<30;i++){now+=33;detector.accept(silent,fft,44100,now);detector.copy(frame,now,true);}
  check(!frame.signal && frame.energy==0 && frame.beatCount==0,"silence must not invent sound or beats");
  // A bass onset near 130 Hz, in a real captured PCM/FFT-shaped byte stream.
  fft[6]=70;detector.accept(waveform(70),fft,44100,now+=33);detector.copy(frame,now,true);
  check(frame.signal && frame.energy>.2f && frame.bass>.5f,"audible bass must yield a strong response");
  check(frame.beatCount==1,"first bass attack must trigger one beat");
  for(int i=0;i<15;i++){detector.accept(waveform(70),fft,44100,now+=33);detector.copy(frame,now,true);}
  check(frame.beatCount==1,"sustained bass must not generate repeated onset events");
  Arrays.fill(fft,(byte)0);for(int i=0;i<15;i++)detector.accept(silent,fft,44100,now+=33);
  fft[6]=90;detector.accept(waveform(80),fft,44100,now+=33);detector.copy(frame,now,true);
  check(frame.beatCount==2,"a later independent bass attack must retrigger");
  detector.copy(frame,now+400,true);check(!frame.signal && frame.bass==0 && frame.energy==0,"stale capture must not keep driving particles");
  detector.copy(frame,now,false);for(float b:frame.bands)check(b==0,"pause must stop spectral output");
  detector.reset();detector.copy(frame,now,true);check(!frame.signal && frame.beatCount==0,"new session must not inherit the prior beat");
  // A high-frequency-only tone may move its spectral lane but is not a bass beat.
  Arrays.fill(fft,(byte)0);fft[240]=60;detector.accept(waveform(40),fft,44100,now+=33);detector.copy(frame,now,true);
  check(frame.signal && frame.beatCount==0,"high frequency must not be mislabeled as a bass onset");
  float peak=0;for(float b:frame.bands)peak=Math.max(peak,b);check(peak>.5f,"high-frequency spectrum must remain visible");
  System.out.println("PASS: silence, bass beat, steady tone, second beat, stale capture, pause, session reset, high-frequency response");
 }
}
