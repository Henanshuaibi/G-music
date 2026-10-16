package cn.pulse.player;
import java.util.*;
public final class DeviceFavoritesCheck {
 private static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
 private static final class Memory implements FavoriteStore.Storage {String data="[]";public String read(){return data;}public void write(String value){data=value;}}
 public static void main(String[] args){try{run();System.exit(0);}catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 private static void run(){
  Memory disk=new Memory();FavoriteStore store=new FavoriteStore(disk);
  PlayerService.Track a=new PlayerService.Track("歌曲\n\"甲\"","歌手","bili:BV1FPjy6TEiE?cid=111",240000);
  PlayerService.Track b=new PlayerService.Track("歌曲乙","歌手","bili:BV1FPjy6TEiE?cid=222",180000);
  check(store.toggle(a),"like adds song");check(store.toggle(b),"different CIDs are separate favorites");
  store=new FavoriteStore(disk);check(store.list().size()==2 && store.contains(a.url),"favorites survive store recreation");check(store.list().get(1).title.equals(a.title),"title escaping round trip");check(store.list().get(0).url.equals(b.url),"most recently liked first");
  PlayerService.Track updated=new PlayerService.Track("更新标题","新歌手",a.url,241000);store.refresh(updated);store=new FavoriteStore(disk);check(store.list().size()==2 && store.list().get(1).expectedMs==241000,"metadata refresh must preserve one favorite");
  check(!store.toggle(updated),"second tap removes like");check(!new FavoriteStore(disk).contains(a.url),"unlike persists");store.toggle(a);
  ArrayList<PlayerService.Track> queue=new ArrayList<>();queue.add(a);queue.clear();check(new FavoriteStore(disk).list().size()==2,"clearing queue does not clear favorites");
  Random random=new Random(17);check(PlaybackOrder.choose(0,-1,0,1,true,random)==-1,"empty queue");check(PlaybackOrder.choose(3,2,0,1,true,random)==0,"list wraps");check(PlaybackOrder.choose(3,1,1,1,true,random)==1,"single repeat on completion");check(PlaybackOrder.choose(3,1,1,1,false,random)==2,"manual skip works in single repeat");check(PlaybackOrder.choose(1,0,2,1,true,random)==0,"one song shuffle");int[] seen=new int[7];int current=0;for(int i=0;i<10000;i++){int next=PlaybackOrder.choose(7,current,2,1,true,random);check(next>=0 && next<7 && next!=current,"shuffle uses only queue, no immediate repeat");seen[next]++;current=next;}for(int count:seen)check(count>1100 && count<1700,"all queue entries reachable without favorites weighting");
  System.out.println("PASS: favorite/unlike persistence, CID identity, metadata, queue independence, repeat-one/manual skip/list wrap, 10,000 shuffle selections");
 }
}
