package cn.pulse.player;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class DeviceSongImportCheck {
 static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 static final class Callbacks implements Executor {final BlockingQueue<Runnable> queue=new LinkedBlockingQueue<>();public void execute(Runnable r){queue.add(r);}void drain(){Runnable next;while((next=queue.poll())!=null)next.run();}}
 public static void main(String[] args){try{run();System.out.println("PASS: batch metadata reads run off UI, ordered results with partial failures/progress, cancellation during a slow read and after queued completion");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run()throws Exception{
  final Thread ui=Thread.currentThread();ExecutorService worker=Executors.newSingleThreadExecutor();
  try{
   Callbacks callbacks=new Callbacks();AtomicInteger commits=new AtomicInteger(),lastProgress=new AtomicInteger();SongImportTask task=new SongImportTask();
   task.start(Arrays.asList("one","broken","two"),source->{check(Thread.currentThread()!=ui,"provider/metadata reading must never run on UI");if(source.equals("broken"))throw new java.io.IOException("bad file");return new PlayerService.Track(source,"artist",source);},worker,callbacks,new SongImportTask.Listener(){
    public void progress(int done,int total){check(Thread.currentThread()==ui && total==3 && done>=lastProgress.get(),"ordered progress delivered on callback executor");lastProgress.set(done);}
    public void complete(List<PlayerService.Track> songs,int failed){check(Thread.currentThread()==ui,"queue mutation belongs on UI");check(failed==1 && songs.size()==2 && songs.get(0).title.equals("one") && songs.get(1).title.equals("two"),"skip unreadable file without reordering successful files");commits.incrementAndGet();}
   });
   worker.submit(()->{}).get(3,TimeUnit.SECONDS);check(commits.get()==0,"no background-thread completion bypass");callbacks.drain();check(commits.get()==1 && lastProgress.get()==3,"progress reaches total and commits once");
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger reads=new AtomicInteger(),events=new AtomicInteger();SongImportTask cancelled=new SongImportTask();
   SongImportTask.Listener unexpected=new SongImportTask.Listener(){public void progress(int d,int t){events.incrementAndGet();}public void complete(List<PlayerService.Track> s,int f){events.incrementAndGet();}};
   cancelled.start(Arrays.asList("slow","never"),source->{reads.incrementAndGet();entered.countDown();boolean waiting=true;while(waiting){try{release.await();waiting=false;}catch(InterruptedException ignored){}}return new PlayerService.Track(source,"a",source);},worker,callbacks,unexpected);
   check(entered.await(3,TimeUnit.SECONDS),"slow provider starts on worker");cancelled.cancel();release.countDown();worker.submit(()->{}).get(3,TimeUnit.SECONDS);callbacks.drain();check(reads.get()==1 && events.get()==0,"cancel stops remaining reads and discards late provider result");
   SongImportTask finished=new SongImportTask();finished.start(Collections.singletonList("queued"),source->new PlayerService.Track(source,"a",source),worker,callbacks,unexpected);worker.submit(()->{}).get(3,TimeUnit.SECONDS);check(!callbacks.queue.isEmpty(),"callbacks waiting for UI");finished.cancel();callbacks.drain();check(events.get()==0,"cancel or destroyed activity cannot receive already queued completion");
  }finally{worker.shutdownNow();}
 }
}
