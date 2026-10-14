package cn.local.jiojio;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoreTest {
    @Test public void separatesFavoriteLabelFromLocationNameAndSupportsLegacy() {
        Position p=new Position(" 公司 ",31,117,1," 地图地点 ");
        assertEquals("公司",p.name);assertEquals("地图地点",p.locationName);
        assertEquals("",new Position("旧收藏",31,117,1).locationName);
    }
    @Test public void acceptsBoundaryCoordinates() {
        assertEquals("90.000000, -180.000000",Position.parse("north","90","-180").coordinates());
        assertEquals(-90,Position.parse("south","-90","180").latitude,0);
    }
    @Test public void rejectsNonFiniteOrOutOfRangeCoordinates() {
        for(String[] pair:new String[][]{{"NaN","0"},{"Infinity","1"},{"91","0"},{"0","181"},{"","0"}}) {
            try {Position.parse("x",pair[0],pair[1]);fail("Should reject invalid location");}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void keysAreLocaleIndependentAndNamesNormalize() {
        java.util.Locale old=java.util.Locale.getDefault();
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY);
            Position p=Position.parse("  ","31.25","121.5");
            assertEquals("31.250000/121.500000",p.key());assertEquals("未命名位置",p.name);
        } finally {java.util.Locale.setDefault(old);}
    }
    @Test public void convertsBytesAndNanosecondsToMegabits() {
        assertEquals(8,NetworkProbe.mbps(1_000_000,1_000_000_000L),0.0001);
        assertEquals(0,NetworkProbe.mbps(0,100),0);
    }
    @Test public void amapCoordinatesConvertToAndFromWgs84() {
        double[] gcj=CoordinateTransform.wgs84ToGcj02(31.2304,121.4737);
        assertTrue(Math.abs(gcj[0]-31.2304)>0.001);
        double[] wgs=CoordinateTransform.gcj02ToWgs84(gcj[0],gcj[1]);
        assertEquals(31.2304,wgs[0],0.00001);
        assertEquals(121.4737,wgs[1],0.00001);
        double[] outside=CoordinateTransform.gcj02ToWgs84(37.7749,-122.4194);
        assertEquals(37.7749,outside[0],0);
        assertEquals(-122.4194,outside[1],0);
    }
    @Test public void refusesUnsafeOrMalformedTestEndpoints() throws Exception {
        for(String value:new String[]{"http://example.com","https://user:pass@example.com","file:///tmp/a","https:///"}) {
            try {NetworkProbe.endpoint(value);fail("Invalid endpoint accepted");}
            catch(java.net.MalformedURLException expected){}
        }
        assertEquals("example.com",NetworkProbe.endpoint("https://example.com/test").getHost());
    }
    @Test public void cancelledProbeMakesNoRequest() throws Exception {
        NetworkProbe probe=new NetworkProbe();probe.cancel();
        try {probe.run("https://example.com/","https://example.com/",(p,n)->{});fail("Cancelled probe ran");}
        catch(java.io.InterruptedIOException expected){}
    }
}
