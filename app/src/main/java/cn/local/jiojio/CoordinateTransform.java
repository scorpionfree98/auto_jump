package cn.local.jiojio;

/** Converts between AMap's GCJ-02 map coordinates and WGS-84 coordinates used by Android Location. */
public final class CoordinateTransform {
    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordinateTransform() {}

    public static double[] gcj02ToWgs84(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) return new double[]{latitude, longitude};
        double wgsLat = latitude, wgsLon = longitude;
        for (int i = 0; i < 8; i++) {
            double[] shifted = wgs84ToGcj02(wgsLat, wgsLon);
            wgsLat += latitude - shifted[0];
            wgsLon += longitude - shifted[1];
        }
        return new double[]{wgsLat, wgsLon};
    }

    public static double[] wgs84ToGcj02(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) return new double[]{latitude, longitude};
        double dLat = transformLatitude(longitude - 105.0, latitude - 35.0);
        double dLon = transformLongitude(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = dLon * 180.0 / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{latitude + dLat, longitude + dLon};
    }

    private static boolean outsideChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLatitude(double x, double y) {
        double value = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*Math.sqrt(Math.abs(x));
        value += (20*Math.sin(6*x*PI) + 20*Math.sin(2*x*PI))*2/3;
        value += (20*Math.sin(y*PI) + 40*Math.sin(y/3*PI))*2/3;
        value += (160*Math.sin(y/12*PI) + 320*Math.sin(y*PI/30))*2/3;
        return value;
    }

    private static double transformLongitude(double x, double y) {
        double value = 300 + x + 2*y + .1*x*x + .1*x*y + .1*Math.sqrt(Math.abs(x));
        value += (20*Math.sin(6*x*PI) + 20*Math.sin(2*x*PI))*2/3;
        value += (20*Math.sin(x*PI) + 40*Math.sin(x/3*PI))*2/3;
        value += (150*Math.sin(x/12*PI) + 300*Math.sin(x/30*PI))*2/3;
        return value;
    }
}
