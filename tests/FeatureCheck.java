package cn.pulse.player;
public final class FeatureCheck {
 private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
 public static void main(String[] args){
  AudioReaction engine=new AudioReaction();AudioReaction.Frame frame=new AudioReaction.Frame();float[] pcm=new float[1024];PcmSignal silence=new PcmSignal(pcm);engine.accept(silence.wave,silence.fft,44100,1000);engine.copy(frame,1000,true);check(!frame.signal,"PCM silence");
  for(int i=0;i<pcm.length;i++)pcm[i]=(float)(.7*Math.sin(2*Math.PI*3*i/1024));PcmSignal signal=new PcmSignal(pcm);engine.accept(signal.wave,signal.fft,44100,1033);engine.copy(frame,1033,true);check(frame.signal && frame.bass>.5 && frame.beatCount==1,"raw PCM must produce a bass beat without any output volume input");
  System.out.println("PASS: raw PCM FFT, silence and source bass without any speaker volume input");
 }
}
