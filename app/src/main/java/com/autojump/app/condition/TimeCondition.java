package com.autojump.app.condition;

import android.content.Context;
import com.autojump.app.bean.Rule;
import java.util.Calendar;

/**
 * 时间段条件
 * 支持：星期几 + 时间范围
 */
public class TimeCondition extends Rule.Condition {
    private int[] daysOfWeek;  // 星期几（1=周一, 7=周日），null 表示每天
    private int startHour;     // 开始小时（0-23）
    private int startMinute;   // 开始分钟（0-59）
    private int endHour;       // 结束小时
    private int endMinute;     // 结束分钟

    public TimeCondition(int[] daysOfWeek, int startHour, int startMinute, int endHour, int endMinute) {
        this.daysOfWeek = daysOfWeek;
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
    }

    @Override
    public boolean match(Context context) {
        Calendar now = Calendar.getInstance();

        // 检查星期几
        if (daysOfWeek != null && daysOfWeek.length > 0) {
            int today = now.get(Calendar.DAY_OF_WEEK);
            // Calendar: 1=周日, 2=周一...7=周六，需要转换
            int todayConverted = (today == 1) ? 7 : (today - 1);
            boolean dayMatch = false;
            for (int day : daysOfWeek) {
                if (day == todayConverted) {
                    dayMatch = true;
                    break;
                }
            }
            if (!dayMatch) return false;
        }

        // 检查时间范围
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;

        if (endMinutes > startMinutes) {
            // 同一天内，如 08:00-18:00
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
        } else {
            // 跨天，如 22:00-02:00
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
        }
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        if (daysOfWeek != null && daysOfWeek.length > 0) {
            sb.append("周");
            String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
            for (int i = 0; i < daysOfWeek.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(dayNames[daysOfWeek[i] - 1]);
            }
            sb.append(" ");
        }
        sb.append(String.format("%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute));
        return sb.toString();
    }

    // Getters and setters
    public int[] getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(int[] daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }
    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }
    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }
    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }
}
