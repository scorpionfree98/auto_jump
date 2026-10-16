package cn.local.jiojio.plate;

import java.util.Locale;

/** Plate geometry uses millimetres as drawing units, not physical screen size. */
public final class PlateSpec {
    public static final int BLUE = 0, GREEN = 1;
    public static final String PROVINCES = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼";
    private PlateSpec() {}
    public static int width(int style) { return style == GREEN ? 480 : 440; }
    public static int length(int style) { return style == GREEN ? 8 : 7; }
    public static float characterWidth(int index,int style) { return style==GREEN && index>0 ? 43 : 45; }
    public static float characterX(int index,int style) {
        if(style==GREEN) return index==0 ? 15.5f : 69.5f+(index-1)*52+(index>=2 ? 40 : 0);
        return 15+index*57+(index>=2 ? 22 : 0);
    }
    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s·•.　]", "").toUpperCase(Locale.ROOT);
    }
    public static String error(String value, int style) {
        String n = normalize(value);
        if (n.length() != length(style)) return style == GREEN ? "新能源车牌需 8 位，例如：皖AD12345" : "普通车牌需 7 位，例如：皖A12345";
        if (PROVINCES.indexOf(n.charAt(0)) < 0) return "第一位请选择省份简称，例如：京、沪、皖";
        if (!n.substring(1, 2).matches("[A-HJ-NP-Z]")) return "第二位请输入城市字母（不含 I、O）";
        if (!n.substring(2).matches("[A-HJ-NP-Z0-9]+")) return "号码部分仅支持数字和英文字母（不含 I、O）";
        return null;
    }
}
