package cn.local.jiojio;

import java.io.*;
import java.net.*;
import java.util.*;

/** Bounded, cancellable HTTPS throughput measurement. No ICMP claims or synthetic values. */
public final class NetworkProbe {
    public static final int DOWNLOAD_BYTES=8*1024*1024, UPLOAD_BYTES=2*1024*1024;
    public interface Listener { void progress(String phase,long bytes); }
    public static final class Result {
        public final double httpsLatencyMs,downloadMbps,uploadMbps;
        public final long downloaded,uploaded;
        Result(double latency,double down,double up,long downloaded,long uploaded) {
            this.httpsLatencyMs=latency;this.downloadMbps=down;this.uploadMbps=up;
            this.downloaded=downloaded;this.uploaded=uploaded;
        }
    }
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public void cancel() { cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect(); }
    public boolean isCancelled() { return cancelled; }
    private void check() throws InterruptedIOException {
        if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("测速已取消");
    }
    public static double mbps(long bytes,long nanos) {
        if(bytes<0||nanos<=0)throw new IllegalArgumentException("Invalid measurement");
        return bytes*8000.0/nanos;
    }
    static URL endpoint(String value) throws MalformedURLException {
        URL u=new URL(value);
        if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null)
            throw new MalformedURLException("测速地址必须为不含账号的 HTTPS URL");
        return u;
    }
    private HttpURLConnection open(URL url) throws IOException {
        check();HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Accept-Encoding","identity");
        check();return c;
    }
    private static void ok(HttpURLConnection c) throws IOException {
        int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("测速服务器返回 HTTP "+code);
    }
    public Result run(String downloadUrl,String uploadUrl,Listener listener) throws IOException {
        URL down=endpoint(downloadUrl),up=endpoint(uploadUrl);
        double[] latency=new double[3];
        java.util.concurrent.ScheduledExecutorService watchdog=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"speed-deadline");t.setDaemon(true);return t;});
        java.util.concurrent.ScheduledFuture<?> deadline=watchdog.schedule(this::cancel,60,java.util.concurrent.TimeUnit.SECONDS);
        try {
            for(int i=0;i<3;i++) {
                listener.progress("HTTPS 响应耗时 "+(i+1)+"/3",0);
                HttpURLConnection c=open(down);c.setRequestMethod("HEAD");long start=System.nanoTime();
                try { ok(c);latency[i]=(System.nanoTime()-start)/1e6; } finally { c.disconnect(); }
            }
            Arrays.sort(latency);
            HttpURLConnection c=open(down);long received=0;long start=System.nanoTime();
            listener.progress("下载测速",0);
            try {
                ok(c);
                try(InputStream in=c.getInputStream()) {
                    byte[] buffer=new byte[32*1024];
                    while(received<DOWNLOAD_BYTES) {
                        check();if(System.nanoTime()-start>20_000_000_000L)throw new IOException("下载测试超过 20 秒，请重试");
                        int n=in.read(buffer,0,(int)Math.min(buffer.length,DOWNLOAD_BYTES-received));
                        if(n<0)break;received+=n;listener.progress("下载测速",received);
                    }
                }
            } finally { c.disconnect(); }
            long downloadNanos=System.nanoTime()-start;
            if(received==0)throw new IOException("服务器没有返回测速数据");
            listener.progress("上传测速",0);
            c=open(up);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(UPLOAD_BYTES);
            c.setRequestProperty("Content-Type","application/octet-stream");long sent=0;start=System.nanoTime();
            byte[] payload=new byte[32*1024];new Random().nextBytes(payload);
            try {
                try(OutputStream out=c.getOutputStream()) {
                    while(sent<UPLOAD_BYTES) {
                        check();if(System.nanoTime()-start>20_000_000_000L)throw new IOException("上传测试超过 20 秒，请重试");
                        int n=(int)Math.min(payload.length,UPLOAD_BYTES-sent);out.write(payload,0,n);sent+=n;listener.progress("上传测速",sent);
                    }
                }
                ok(c); // Report upload only after server accepts the complete request.
            } finally { c.disconnect(); }
            check();return new Result(latency[1],mbps(received,downloadNanos),mbps(sent,System.nanoTime()-start),received,sent);
        } finally { deadline.cancel(false);watchdog.shutdownNow();HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null; }
    }
}
