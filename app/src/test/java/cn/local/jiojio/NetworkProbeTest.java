package cn.local.jiojio;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Deterministic failures and cancellation, without changing the phone's network. */
public class NetworkProbeTest {
    private static final String ENDPOINT = "https://example.com/test";
    private static final class FakeConnection extends HttpURLConnection {
        boolean disconnected;
        int status = 200;
        FakeConnection() throws Exception { super(new URL(ENDPOINT)); }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
        public int getResponseCode() { return status; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[4096]); }
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
    }
    @Test public void connectionFailureIsNotSuccessAndFreshProbeRecovers() throws Exception {
        NetworkProbe failed = new NetworkProbe(url -> { throw new UnknownHostException("offline"); });
        try { failed.run(ENDPOINT, ENDPOINT, (p,n)->{}); fail("Offline probe succeeded"); }
        catch (UnknownHostException expected) { assertFalse(failed.isCancelled()); }
        List<FakeConnection> opened = new ArrayList<>();
        NetworkProbe fresh = new NetworkProbe(url -> {
            try { FakeConnection c = new FakeConnection(); opened.add(c); return c; }
            catch (Exception e) { throw new IOException(e); }
        });
        NetworkProbe.Result result = fresh.run(ENDPOINT, ENDPOINT, (p,n)->{});
        assertEquals(4096, result.downloaded);
        assertEquals(NetworkProbe.UPLOAD_BYTES, result.uploaded);
        assertEquals(5, opened.size());
        for (FakeConnection c : opened) assertTrue(c.disconnected);
    }
    @Test public void cancellingDuringDownloadDisconnectsAndNeverUploads() throws Exception {
        List<FakeConnection> opened = new ArrayList<>();
        NetworkProbe probe = new NetworkProbe(url -> {
            try { FakeConnection c = new FakeConnection(); opened.add(c); return c; }
            catch (Exception e) { throw new IOException(e); }
        });
        try {
            probe.run(ENDPOINT, ENDPOINT, (phase,n)->{ if (n > 0) probe.cancel(); });
            fail("Cancelled probe returned a result");
        } catch (InterruptedIOException expected) { assertTrue(probe.isCancelled()); }
        assertEquals(4, opened.size());
        for (FakeConnection c : opened) assertTrue(c.disconnected);
    }
    @Test public void rejectedUploadNeverProducesSuccess() throws Exception {
        List<FakeConnection> opened = new ArrayList<>();
        NetworkProbe probe = new NetworkProbe(url -> {
            try {
                FakeConnection c = new FakeConnection(); opened.add(c);
                if (opened.size() == 5) c.status = 503;
                return c;
            } catch (Exception e) { throw new IOException(e); }
        });
        try { probe.run(ENDPOINT, ENDPOINT, (p,n)->{}); fail("Rejected upload succeeded"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("503")); }
        for (FakeConnection c : opened) assertTrue(c.disconnected);
    }
}
