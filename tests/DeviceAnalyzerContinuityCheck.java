package cn.pulse.player;
import android.os.SystemClock;
import android.media.MediaDataSource;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Real MediaExtractor/MediaCodec runs with a playback clock, plus delayed range-server reads. */
public final class DeviceAnalyzerContinuityCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static final int BLOCK=256*1024;
 static class Server implements AutoCloseable {
  final byte[] data;final ServerSocket socket=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
  final ExecutorService clients=Executors.newCachedThreadPool();final ConcurrentHashMap<Long,AtomicInteger> requests=new ConcurrentHashMap<>();
  final CountDownLatch aheadStarted=new CountDownLatch(1),aheadDone=new CountDownLatch(1);
  volatile boolean closed;volatile int delay;volatile long failedBlock=-1;
  Server(byte[] data,int delay)throws Exception{this.data=data;this.delay=delay;clients.execute(()->{while(!closed)try{Socket client=socket.accept();clients.execute(()->respond(client));}catch(IOException e){if(!closed)throw new RuntimeException(e);}});}
  String url(){return "http://127.0.0.1:"+socket.getLocalPort()+"/audio";}
  void respond(Socket client){try(Socket c=client){BufferedReader input=new BufferedReader(new InputStreamReader(c.getInputStream(),"US-ASCII"));String line;long start=0,end=data.length-1;while((line=input.readLine())!=null && !line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("range:")){String range=line.substring(line.indexOf('=')+1);String[] parts=range.split("-");start=Long.parseLong(parts[0]);end=Long.parseLong(parts[1]);}
    int count=requests.computeIfAbsent(start,k->new AtomicInteger()).incrementAndGet();OutputStream out=c.getOutputStream();
    if(start==failedBlock && count==1){out.write("HTTP/1.1 503 Busy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));out.flush();return;}
    if(start==BLOCK)aheadStarted.countDown();if(start>0 && delay>0)Thread.sleep(delay);
    if(start>=data.length){out.write(("HTTP/1.1 416 Range\r\nContent-Range: bytes */"+data.length+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));return;}
    end=Math.min(end,data.length-1);int length=(int)(end-start+1);out.write(("HTTP/1.1 206 Partial Content\r\nContent-Type: audio/wav\r\nContent-Range: bytes "+start+"-"+end+"/"+data.length+"\r\nContent-Length: "+length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(data,(int)start,length);out.flush();if(start==BLOCK)aheadDone.countDown();
   }catch(Exception e){if(!closed)throw new RuntimeException(e);}}
  public void close(){closed=true;try{socket.close();}catch(IOException ignored){}clients.shutdownNow();}
 }
 static byte[] read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 public static void main(String[] args){try{run(new File(args[0]));System.out.println("PASS: continuous native PCM analysis at 44.1/96/192 kHz, seeks, delayed HTTP block boundaries, asynchronous prefetch, retry and close");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
 static void run(File folder)throws Exception{
  ExecutorService checks=Executors.newFixedThreadPool(4);ArrayList<Future<?>> results=new ArrayList<>();
  try{
   for(int rate:new int[]{44100,96000,192000})results.add(checks.submit(()->{PcmAnalyzer decoder=new PcmAnalyzer(null,new File(folder,"tone-"+rate+".wav").getPath(),new HashMap<>(),0);try{continuous(decoder,9000,"local "+rate);seek(decoder,13000);seek(decoder,500);}finally{decoder.close();}return null;}));
   byte[] wav=read(new File(folder,"tone-44100.wav"));
   results.add(checks.submit(()->{try(Server server=new Server(wav,1200)){HttpAudioSource probe=new HttpAudioSource(server.url(),Collections.emptyMap());try{check(probe.getSize()==wav.length,"HTTP test server serves full WAV");byte[] header=new byte[44];check(probe.readAt(0,header,0,44)==44 && header[0]==82,"HTTP WAV header intact");}finally{probe.close();}PcmAnalyzer decoder=new PcmAnalyzer(()->new HttpAudioSource(server.url(),Collections.emptyMap()),0);try{continuous(decoder,11000,"HTTP with 1.2 s block delay");}finally{decoder.close();}}return null;}));
   for(Future<?> result:results)result.get(35,TimeUnit.SECONDS);
   try(Server server=new Server(wav,700)){HttpAudioSource source=new HttpAudioSource(server.url(),Collections.emptyMap());try{
    check(source.getSize()==wav.length,"range source length");check(server.aheadStarted.await(3,TimeUnit.SECONDS),"next block requested before playback crosses the boundary");check(server.aheadDone.await(3,TimeUnit.SECONDS),"prefetch completes independently");Thread.sleep(40);
    byte[] slice=new byte[500];long start=SystemClock.uptimeMillis();check(source.readAt(BLOCK+19,slice,0,slice.length)==slice.length,"prefetched block is readable");check(SystemClock.uptimeMillis()-start<200,"cache hit never waits for slow network");for(int i=0;i<slice.length;i++)check(slice[i]==wav[BLOCK+19+i],"cached bytes preserved");check(server.requests.get((long)BLOCK).get()==1,"prefetch and demanded read share one request");
    check(source.readAt(wav.length-12,slice,0,slice.length)==12,"last block returns partial read");check(source.readAt(wav.length,slice,0,1)==-1,"EOF is retained");
   }finally{source.close();}try{source.readAt(0,new byte[1],0,1);throw new AssertionError("closed source must reject reads");}catch(IOException expected){}}
   try(Server server=new Server(wav,0)){server.failedBlock=0;HttpAudioSource source=new HttpAudioSource(server.url(),Collections.emptyMap());try{check(source.getSize()==wav.length && server.requests.get(0L).get()==2,"transient range failure retries without ending analysis");}finally{source.close();}}
   AtomicInteger attempts=new AtomicInteger();PcmAnalyzer recovering=new PcmAnalyzer(()->{if(attempts.incrementAndGet()==1)throw new IOException("temporary extractor source failure");return new Bytes(wav);},0);try{awaitFrame(recovering,0,5000);check(attempts.get()==2,"decoder reopens source after a temporary error");}finally{recovering.close();}
   AtomicInteger restarts=new AtomicInteger();PcmAnalyzer truncated=new PcmAnalyzer(()->new Bytes(wav,restarts.incrementAndGet()==1?BLOCK:wav.length),0);try{long deadline=SystemClock.uptimeMillis()+6000;while(restarts.get()<2 && SystemClock.uptimeMillis()<deadline)Thread.sleep(50);check(restarts.get()==2,"premature end of a known-duration stream triggers recovery");awaitFrame(truncated,0,3000);}finally{truncated.close();}
  }finally{checks.shutdownNow();}
 }
 static final class Bytes extends MediaDataSource {final byte[] bytes;final int limit;Bytes(byte[] data){this(data,data.length);}Bytes(byte[] data,int limit){bytes=data;this.limit=limit;}public int readAt(long position,byte[] buffer,int offset,int size){if(position>=limit)return -1;int n=Math.min(size,limit-(int)position);System.arraycopy(bytes,(int)position,buffer,offset,n);return n;}public long getSize(){return bytes.length;}public void close(){}}
 static void awaitFrame(PcmAnalyzer decoder,int position,int timeout)throws Exception{AudioReaction reaction=new AudioReaction();long deadline=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<deadline){if(decoder.feed(position,reaction,SystemClock.uptimeMillis()))return;Thread.sleep(25);}throw new AssertionError("No frame at "+position+": "+decoder.diagnostic()+" / "+decoder.status());}
 static void seek(PcmAnalyzer decoder,int position)throws Exception{decoder.seek(position);awaitFrame(decoder,position,3000);}
 static void continuous(PcmAnalyzer decoder,int duration,String label)throws Exception{
  awaitFrame(decoder,0,7000);AudioReaction reaction=new AudioReaction();AudioReaction.Frame frame=new AudioReaction.Frame();long start=SystemClock.uptimeMillis(),gapStart=0,maxGap=0;int misses=0,total=0;
  while(SystemClock.uptimeMillis()-start<duration){long now=SystemClock.uptimeMillis();int position=(int)(now-start);boolean ok=decoder.feed(position,reaction,now);reaction.copy(frame,now,true);total++;if(!ok || !frame.signal){misses++;if(gapStart==0)gapStart=now;maxGap=Math.max(maxGap,now-gapStart);}else gapStart=0;Thread.sleep(33);}
  check(maxGap<300 && misses<total/10,label+" stalls: maxGap="+maxGap+" misses="+misses+"/"+total+" "+decoder.diagnostic());System.out.println("PASS: "+label+" maxGap="+maxGap+" ms, misses="+misses+"/"+total);
 }
}
