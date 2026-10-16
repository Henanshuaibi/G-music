package cn.pulse.player;
import android.media.MediaDataSource;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Bounded range cache with one asynchronous look-ahead request. No network I/O holds the cache lock. */
public final class HttpAudioSource extends MediaDataSource {
 private static final int BLOCK=256*1024;
 private final String url;private final Map<String,String> headers;private volatile long length=-1;
 private volatile boolean closed;private boolean prefetching;
 private final Object lock=new Object();
 private final Set<HttpURLConnection> connections=new HashSet<>();
 private final Map<Long,FutureTask<byte[]>> pending=new HashMap<>();
 private final LinkedHashMap<Long,byte[]> cache=new LinkedHashMap<Long,byte[]>(8,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,byte[]> e){return size()>6;}};
 private final ExecutorService ahead=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pulse-audio-ahead");t.setDaemon(true);return t;});
 public HttpAudioSource(String u,Map<String,String> h){url=u;headers=new HashMap<>(h);}
 public long getSize()throws IOException{if(length<0 && !closed){block(0);prefetch(BLOCK);}return length;}
 public int readAt(long position,byte[] buffer,int offset,int size)throws IOException{
  if(closed)throw new IOException("source closed");if(position<0 || offset<0 || size<0 || offset>buffer.length-size)throw new IOException("invalid audio range");if(size==0)return 0;if(length>=0 && position>=length)return -1;
  int copied=0;while(copied<size){long start=(position/BLOCK)*BLOCK;byte[] data=block(start);prefetch(start+BLOCK);int inside=(int)(position-start);if(inside>=data.length)break;int amount=Math.min(size-copied,data.length-inside);System.arraycopy(data,inside,buffer,offset+copied,amount);position+=amount;copied+=amount;if(data.length<BLOCK)break;}return copied==0?-1:copied;
 }
 private byte[] block(long start)throws IOException{
  FutureTask<byte[]> task;boolean owner=false;
  synchronized(lock){if(closed)throw new IOException("source closed");byte[] saved=cache.get(start);if(saved!=null)return saved;task=pending.get(start);if(task==null){task=new FutureTask<>(()->fetchAndCache(start));pending.put(start,task);owner=true;}}
  if(owner)task.run();
  try{return task.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("audio read interrupted",e);}catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof IOException)throw (IOException)cause;throw new IOException("audio read failed",cause);}catch(CancellationException e){throw new IOException("source closed",e);}
 }
 private byte[] fetchAndCache(long start)throws IOException{
  try{byte[] data=null;for(int attempt=0;attempt<2;attempt++){try{data=fetch(start);break;}catch(IOException e){if(closed || attempt==1)throw e;}}
   synchronized(lock){if(closed)throw new IOException("source closed");cache.put(start,data);}return data;
  }finally{synchronized(lock){pending.remove(start);}}
 }
 private void prefetch(long start){
  synchronized(lock){if(closed || prefetching || (length>=0 && start>=length) || cache.containsKey(start) || pending.containsKey(start))return;prefetching=true;
   try{ahead.execute(()->{try{block(start);}catch(IOException ignored){}finally{synchronized(lock){prefetching=false;}}});}catch(RejectedExecutionException e){prefetching=false;}
  }
 }
 private byte[] fetch(long start)throws IOException{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
  synchronized(lock){if(closed)throw new IOException("source closed");connections.add(c);}
  c.setConnectTimeout(4000);c.setReadTimeout(4000);for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());c.setRequestProperty("Range","bytes="+start+"-"+(start+BLOCK-1));c.setRequestProperty("Accept-Encoding","identity");
  try{int code=c.getResponseCode();String range=c.getHeaderField("Content-Range");
   if(range!=null){int slash=range.lastIndexOf('/');if(slash>=0)try{length=Long.parseLong(range.substring(slash+1));}catch(NumberFormatException ignored){}}
   else if(code==200)length=c.getContentLengthLong();
   if(code==416)return new byte[0];if(code!=206 && !(code==200 && start==0))throw new IOException("audio range HTTP "+code);
   if(code==206 && (range==null || !range.startsWith("bytes "+start+"-")))throw new IOException("audio range offset mismatch");
   ByteArrayOutputStream out=new ByteArrayOutputStream(BLOCK);try(InputStream in=c.getInputStream()){byte[] buf=new byte[16384];int n;while(out.size()<BLOCK && !closed && (n=in.read(buf,0,Math.min(buf.length,BLOCK-out.size())))!=-1)out.write(buf,0,n);}
   if(closed)throw new IOException("source closed");byte[] data=out.toByteArray();long expected=length<0?-1:Math.min(BLOCK,Math.max(0,length-start));if(expected>=0 && data.length<expected)throw new EOFException("incomplete audio block");return data;
  }finally{c.disconnect();synchronized(lock){connections.remove(c);}}
 }
 public void close(){ArrayList<HttpURLConnection> active;
  synchronized(lock){if(closed)return;closed=true;active=new ArrayList<>(connections);for(FutureTask<byte[]> task:pending.values())task.cancel(true);pending.clear();cache.clear();}
  ahead.shutdownNow();for(HttpURLConnection c:active)c.disconnect();
 }
}
