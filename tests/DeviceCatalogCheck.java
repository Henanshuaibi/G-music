package cn.pulse.player;
public final class DeviceCatalogCheck {
 private static void check(boolean p,String reason){if(!p)throw new AssertionError(reason);}
 public static void main(String[] args){try{run();System.exit(0);}catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 private static void run()throws Exception{
  BiliClient.Result collection=BiliClient.open("BV1FPjy6TEiE?p=2");check(collection.mime.startsWith("audio/"),"downloadable independent audio MIME");check(collection.parts.size()==50,"must expand all 50 parts");check(collection.selected==1,"must honor selected part");
  int total=0;for(PlayerService.Track t:collection.parts)total+=t.expectedMs;check(total>3*60*60*1000,"collection must retain more than 3 hours");
  String second=collection.parts.get(1).url;BiliClient.Result exact=BiliClient.open(second.substring(5));check(exact.parts.size()==1,"replaying a part must not duplicate collection");check(exact.parts.get(0).url.equals(second),"CID must remain selected");check(exact.expectedMs==collection.parts.get(1).expectedMs,"part duration must be preserved");
  BiliClient.Result xu=BiliClient.open("BV1bH4y1k7y8");check(xu.parts.size()==100,"Xu Liang collection must contain 100 parts");
  System.out.println("PASS: 50-part collection, selected P2, exact CID without duplication, total duration "+total/60000+" min, Xu Liang 100 parts");
 }
}
