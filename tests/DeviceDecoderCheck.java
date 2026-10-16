package cn.pulse.player;
import java.util.HashMap;
public final class DeviceDecoderCheck {
 public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 private static void run(String[] args)throws Exception{
  HashMap<String,String> headers=new HashMap<>();headers.put("Referer","https://www.bilibili.com/");headers.put("User-Agent","Mozilla/5.0");int base=args.length>1?Integer.parseInt(args[1]):0;
  PcmAnalyzer decoder=new PcmAnalyzer(null,args[0],headers,base);AudioReaction analysis=new AudioReaction();AudioReaction.Frame f=new AudioReaction.Frame();boolean received=false;
  long deadline=android.os.SystemClock.uptimeMillis()+15000;
  try{while(android.os.SystemClock.uptimeMillis()<deadline){long now=android.os.SystemClock.uptimeMillis();if(decoder.feed(base+100,analysis,now)){analysis.copy(f,now,true);if(f.signal && f.energy>.05f){received=true;break;}}Thread.sleep(50);}if(!received)throw new AssertionError("No decoded signal: "+decoder.status()+" / "+decoder.diagnostic());
   decoder.seek(base+1100);Thread.sleep(200);received=false;for(int i=0;i<80;i++){long now=android.os.SystemClock.uptimeMillis();if(decoder.feed(base+1100,analysis,now)){analysis.copy(f,now,true);if(f.signal){received=true;break;}}Thread.sleep(30);}if(!received)throw new AssertionError("Seek not analyzed: "+decoder.status());
   decoder.seek(base+100);Thread.sleep(200);received=false;for(int i=0;i<80;i++){long now=android.os.SystemClock.uptimeMillis();if(decoder.feed(base+100,analysis,now)){analysis.copy(f,now,true);if(f.signal){received=true;break;}}Thread.sleep(30);}if(!received)throw new AssertionError("Backward seek not analyzed: "+decoder.status());
   System.out.println("PASS: device MediaExtractor/MediaCodec decoded PCM; forward/backward seeking; no playback output created");
  }finally{decoder.close();}
 }
}
