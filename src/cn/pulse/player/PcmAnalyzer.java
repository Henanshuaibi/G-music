package cn.pulse.player;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.*;
import java.util.*;

/** A bounded, foreground-only analysis decoder; it never sends audio to a speaker. */
public final class PcmAnalyzer implements AutoCloseable {
 private static final int AHEAD_MS=6000, HISTORY_MS=800, PACKET_STEP_MS=20, MAX_PACKETS=384;
 interface SourceFactory {MediaDataSource open()throws Exception;}
 private static final class Packet {final long ms;final int rate;final PcmSignal signal;Packet(long t,int r,float[] pcm){ms=t;rate=r;signal=new PcmSignal(pcm);}}
 private final ArrayDeque<Packet> packets=new ArrayDeque<>();
 private volatile boolean closed;private volatile int wanted,seekVersion;private volatile String state="正在分析原始音频…";
 private volatile MediaDataSource remoteSource;private final Thread worker;private volatile String diagnostic="";
 public String diagnostic(){synchronized(packets){return diagnostic+" packets="+packets.size()+" range="+(packets.isEmpty()?"empty":packets.peekFirst().ms+".."+packets.peekLast().ms)+" wanted="+wanted;}}
 public PcmAnalyzer(Context context,String source,Map<String,String> headers,int position){this(context,source,headers,position,null);}
 PcmAnalyzer(SourceFactory factory,int position){this(null,"",Collections.emptyMap(),position,factory);}
 private PcmAnalyzer(Context context,String source,Map<String,String> headers,int position,SourceFactory factory){
  wanted=position;worker=new Thread(()->decode(context==null?null:context.getApplicationContext(),source,headers,factory),"pulse-pcm");worker.start();
 }
 public String status(){return state;}
 public boolean feed(int position,AudioReaction reaction,long now){
  wanted=position;Packet selected=null;
  synchronized(packets){for(Packet p:packets){if(p.ms<=position+25 && (selected==null || p.ms>selected.ms))selected=p;}}
  if(selected==null || Math.abs(selected.ms-position)>150)return false;
  reaction.accept(selected.signal.wave,selected.signal.fft,selected.rate,now);return true;
 }
 public void seek(int position){wanted=position;seekVersion++;}
 public void close(){closed=true;closeSource();worker.interrupt();synchronized(packets){packets.clear();}}
 private void closeSource(){MediaDataSource source=remoteSource;if(source!=null)try{source.close();}catch(Exception ignored){}}
 private void decode(Context context,String source,Map<String,String> headers,SourceFactory factory){
  int failures=0;
  while(!closed){
   try{decodeOnce(context,source,headers,factory);return;}catch(InterruptedException e){return;}catch(Exception e){
    if(closed)return;diagnostic=e.getClass().getSimpleName()+": "+e.getMessage();state="音频分析暂时中断，正在重试…";
    try{Thread.sleep(Math.min(8000,1000L<<Math.min(3,failures++)));}catch(InterruptedException interrupted){return;}
   }
  }
 }
 private void decodeOnce(Context context,String source,Map<String,String> headers,SourceFactory factory)throws Exception{
  MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
  try{
   if(factory!=null){remoteSource=factory.open();if(closed)return;extractor.setDataSource(remoteSource);}else if(source.startsWith("content:"))extractor.setDataSource(context,Uri.parse(source),headers);else if(source.startsWith("https://")){remoteSource=new HttpAudioSource(source,headers);if(closed)return;extractor.setDataSource(remoteSource);}else extractor.setDataSource(source,headers);MediaFormat format=null;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){extractor.selectTrack(i);format=f;break;}}
   if(format==null)throw new IllegalStateException("没有音频轨道");
   codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
   int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=AudioFormat.ENCODING_PCM_16BIT;
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnd=false,outputEnd=false;long durationMs=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION)/1000:-1;
   float[] mono=new float[PcmSignal.SIZE];int count=0,seenSeek=seekVersion;long packetTime=0,decoded=wanted,lastPacket=-1;boolean replaceBuffer=true;
   if(wanted>0)extractor.seekTo(wanted*1000L,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
   while(!closed){
    int version=seekVersion,target=wanted;
    if(seenSeek!=version){
     seenSeek=version;
     extractor.seekTo(Math.max(0,target-150)*1000L,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);codec.flush();inputEnd=outputEnd=false;count=0;decoded=target;lastPacket=-1;replaceBuffer=true;synchronized(packets){packets.clear();}
    }
    // A slow network read is normal elapsed playback, not a user seek.
    if(outputEnd || decoded>target+AHEAD_MS){Thread.sleep(30);continue;}
    int input=inputEnd?-1:codec.dequeueInputBuffer(10000);
    if(input>=0){ByteBuffer b=codec.getInputBuffer(input);b.clear();int size=inputEnd?-1:extractor.readSampleData(b,0);if(size<0){codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}else{codec.queueInputBuffer(input,0,size,extractor.getSampleTime(),0);extractor.advance();}}
    int output=codec.dequeueOutputBuffer(info,10000);
    if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;}
    else if(output>=0){
     ByteBuffer b=codec.getOutputBuffer(output).order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);
     int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_8BIT?1:2;
     if(encoding!=AudioFormat.ENCODING_PCM_FLOAT && encoding!=AudioFormat.ENCODING_PCM_16BIT && encoding!=AudioFormat.ENCODING_PCM_8BIT)throw new IllegalStateException("不支持该音频采样格式");
     int frame=0;
     while(b.remaining()>=channels*bytes){
      if(count==0)packetTime=info.presentationTimeUs/1000+frame*1000L/rate;
      float value=0;for(int ch=0;ch<channels;ch++)value+=encoding==AudioFormat.ENCODING_PCM_FLOAT?b.getFloat():encoding==AudioFormat.ENCODING_PCM_8BIT?((b.get()&255)-128)/128f:b.getShort()/32768f;
      mono[count++]=value/channels;frame++;
      if(count==mono.length){
       // Keep roughly 50 analysis windows per second at every sample rate.
       // A fixed number of 1024-sample packets otherwise evicts "now" at 96/192 kHz.
       if(packetTime>=wanted-HISTORY_MS && (lastPacket<0 || packetTime-lastPacket>=PACKET_STEP_MS)){
        Packet p=new Packet(packetTime,rate,mono);synchronized(packets){if(replaceBuffer){packets.clear();replaceBuffer=false;}packets.addLast(p);while(packets.size()>MAX_PACKETS || (!packets.isEmpty() && packets.peekFirst().ms<wanted-HISTORY_MS))packets.removeFirst();}
        lastPacket=packetTime;state="原始音频节奏 · 不受系统音量影响";diagnostic="";
       }
       count=0;decoded=packetTime;
      }
     }
     outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(output,false);
     if(outputEnd && durationMs>0 && decoded+1000<durationMs)throw new java.io.IOException("音频分析数据提前结束");
    }
   }
  }finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}try{extractor.release();}catch(Exception ignored){}closeSource();remoteSource=null;}
 }
}
