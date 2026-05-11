package com.ballbattle;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CheckInSystem {
    private SharedPreferences prefs;

    // 签到奖励（7天一个周期）
    public static final int[] DAILY_REWARDS = {100, 150, 200, 250, 300, 350, 500};
    public static final String[] REWARD_NAMES = {"100金币", "150金币", "200金币", "250金币", "300金币", "350金币", "500金币"};

    public CheckInSystem(Context context) {
        prefs = context.getSharedPreferences("checkin_data", Context.MODE_PRIVATE);
    }

    private String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String getYesterdayKey() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(cal.getTime());
    }

    public int getConsecutiveDays() {
        return prefs.getInt("consecutive_days", 0);
    }

    public boolean hasCheckedInToday() {
        return prefs.getBoolean("checked_" + getTodayKey(), false);
    }

    public boolean checkIn() {
        if (hasCheckedInToday()) return false;

        String today = getTodayKey();
        String lastCheckIn = prefs.getString("last_checkin", "");
        String yesterday = getYesterdayKey();

        int consecutive;

        if (lastCheckIn.isEmpty()) {
            // 第一次签到
            consecutive = 1;
        } else if (lastCheckIn.equals(yesterday)) {
            // 昨天签到了，连续天数+1
            consecutive = prefs.getInt("consecutive_days", 0) + 1;
            if (consecutive > 7) consecutive = 1;  // 超过7天重新开始
        } else {
            // 中断了，重新开始
            consecutive = 1;
        }

        prefs.edit()
            .putBoolean("checked_" + today, true)
            .putString("last_checkin", today)
            .putInt("consecutive_days", consecutive)
            .apply();

        return true;
    }

    public int getTodayReward() {
        int day = getConsecutiveDays();
        if (day <= 0) day = 1;
        if (day > 7) day = 7;
        return DAILY_REWARDS[day - 1];
    }

    public String getTodayRewardName() {
        int day = getConsecutiveDays();
        if (day <= 0) day = 1;
        if (day > 7) day = 7;
        return REWARD_NAMES[day - 1];
    }

    public int getCurrentDay() {
        int day = getConsecutiveDays();
        if (day <= 0) return 1;
        if (day > 7) return 7;
        return day;
    }
}
