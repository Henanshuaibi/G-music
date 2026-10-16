package cn.pulse.player;
/** Fourier analysis of decoded PCM before any device volume is applied. */
public final class PcmSignal {
 public static final int SIZE=1024;
 public final byte[] wave=new byte[SIZE],fft=new byte[SIZE];
 public PcmSignal(float[] samples){
  double[] re=new double[SIZE],im=new double[SIZE];
  for(int i=0;i<SIZE;i++){float value=Math.max(-1,Math.min(1,samples[i]));wave[i]=(byte)Math.max(0,Math.min(255,128+Math.round(value*127)));re[i]=value*(.5-.5*Math.cos(2*Math.PI*i/(SIZE-1)));}
  for(int i=1,j=0;i<SIZE;i++){int bit=SIZE>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=re[i];re[i]=re[j];re[j]=t;}}
  for(int len=2;len<=SIZE;len<<=1){double angle=-2*Math.PI/len;for(int i=0;i<SIZE;i+=len){for(int k=0;k<len/2;k++){double c=Math.cos(angle*k),s=Math.sin(angle*k);int a=i+k,b=a+len/2;double rr=re[b]*c-im[b]*s,ii=re[b]*s+im[b]*c;re[b]=re[a]-rr;im[b]=im[a]-ii;re[a]+=rr;im[a]+=ii;}}}
  for(int k=1;k<SIZE/2;k++){fft[2*k]=quantize(re[k]*508/SIZE);fft[2*k+1]=quantize(im[k]*508/SIZE);}
 }
 private static byte quantize(double x){return (byte)Math.max(-127,Math.min(127,Math.round(x)));}
}
