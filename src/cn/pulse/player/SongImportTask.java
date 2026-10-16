package cn.pulse.player;
import java.util.*;
import java.util.concurrent.*;
/** Reads one file at a time on a worker; cancellation also invalidates queued UI callbacks. */
final class SongImportTask {
 interface Reader {PlayerService.Track read(String source)throws Exception;}
 interface Listener {void progress(int done,int total);void complete(List<PlayerService.Track> songs,int failed);}
 private volatile boolean cancelled;
 private volatile Listener listener;
 private Future<?> future;
 void start(List<String> sources,Reader reader,ExecutorService worker,Executor callbacks,Listener target){
  listener=target;ArrayList<String> inputs=new ArrayList<>(sources);
  future=worker.submit(()->{
   ArrayList<PlayerService.Track> songs=new ArrayList<>();int failed=0;long last=0;
   for(int i=0;i<inputs.size();i++){
    if(cancelled || Thread.currentThread().isInterrupted())return;
    try{PlayerService.Track song=reader.read(inputs.get(i));if(song==null)failed++;else songs.add(song);}catch(Exception e){failed++;}
    if(cancelled || Thread.currentThread().isInterrupted())return;
    int done=i+1;long now=System.nanoTime();
    if(done==inputs.size() || now-last>=80000000L){last=now;callbacks.execute(()->{Listener current=listener;if(!cancelled && current!=null)current.progress(done,inputs.size());});}
   }
   final int errors=failed;callbacks.execute(()->{Listener current=listener;if(!cancelled && current!=null){listener=null;current.complete(songs,errors);}});
  });
 }
 void cancel(){cancelled=true;listener=null;Future<?> pending=future;if(pending!=null)pending.cancel(true);}
}
