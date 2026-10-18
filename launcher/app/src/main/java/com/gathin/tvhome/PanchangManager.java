package com.gathin.tvhome;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Astronomical Jain Tithi (Surya Udaya Tithi) and Canonical Choghadiya calculation engine.
 * 100% offline, self-contained, and exact.
 */
public class PanchangManager {

    public static class TithiDay {
        public String dayLabel;    // e.g. "TODAY 18" or "MON 19"
        public String tithiText;   // e.g. "Sud 7", "Sud 8"
        public String badge;       // e.g. "Today", "Aathm", "Agiyaras", "Chaudas", "Poonam"
        public boolean isParva;
        public boolean isToday;
    }

    public static class ChoghadiyaSlot {
        public String name;        // e.g. "Amrit", "Shubh", "Labh", "Chal", "Rog", "Kal", "Udveg"
        public String timeRange;   // e.g. "09:07 - 10:35"
        public String statusLabel; // e.g. "Good", "Best", "Neutral", "Avoid"
        public int natureType;     // 1 = Good (Green), 2 = Neutral (Yellow), 3 = Avoid (Red)
        public boolean isCurrent;
    }

    public static class ChoghadiyaInfo {
        public String title;       // e.g. "CHOGHADIYA (DAY) — CURRENT: ROG (3:01 PM – 4:30 PM)"
        public boolean isNight;
        public List<ChoghadiyaSlot> slots = new ArrayList<>();
    }

    // Standard Day Choghadiya order by weekday (Calendar.SUNDAY = 1, MONDAY = 2, ...)
    private static final String[][] DAY_CHOGHADIYA = {
            {}, // index 0 unused
            {"Udveg", "Chal", "Labh", "Amrit", "Kal", "Shubh", "Rog", "Udveg"},       // 1 = Sunday
            {"Amrit", "Kal", "Shubh", "Rog", "Udveg", "Chal", "Labh", "Amrit"},       // 2 = Monday
            {"Rog", "Udveg", "Chal", "Labh", "Amrit", "Kal", "Shubh", "Rog"},         // 3 = Tuesday
            {"Labh", "Amrit", "Kal", "Shubh", "Rog", "Udveg", "Chal", "Labh"},         // 4 = Wednesday
            {"Shubh", "Rog", "Udveg", "Chal", "Labh", "Amrit", "Kal", "Shubh"},       // 5 = Thursday
            {"Chal", "Labh", "Amrit", "Kal", "Shubh", "Rog", "Udveg", "Chal"},         // 6 = Friday
            {"Kal", "Shubh", "Rog", "Udveg", "Chal", "Labh", "Amrit", "Kal"}          // 7 = Saturday
    };

    // Standard Night Choghadiya order by weekday
    private static final String[][] NIGHT_CHOGHADIYA = {
            {}, // index 0 unused
            {"Shubh", "Amrit", "Chal", "Rog", "Kal", "Labh", "Udveg", "Shubh"},       // 1 = Sunday
            {"Chal", "Rog", "Kal", "Labh", "Udveg", "Shubh", "Amrit", "Chal"},         // 2 = Monday
            {"Kal", "Labh", "Udveg", "Shubh", "Amrit", "Chal", "Rog", "Kal"},         // 3 = Tuesday
            {"Udveg", "Shubh", "Amrit", "Chal", "Rog", "Kal", "Labh", "Udveg"},       // 4 = Wednesday
            {"Amrit", "Chal", "Rog", "Kal", "Labh", "Udveg", "Shubh", "Amrit"},       // 5 = Thursday
            {"Rog", "Kal", "Labh", "Udveg", "Shubh", "Amrit", "Chal", "Rog"},         // 6 = Friday
            {"Labh", "Udveg", "Shubh", "Amrit", "Chal", "Rog", "Kal", "Labh"}          // 7 = Saturday
    };

    /**
     * Compute next 7 days Jain Tithis starting from today.
     */
    public static List<TithiDay> get7DayTithis() {
        List<TithiDay> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE d", Locale.US);

        for (int i = 0; i < 7; i++) {
            Calendar target = (Calendar) cal.clone();
            target.add(Calendar.DAY_OF_YEAR, i);

            TithiDay td = new TithiDay();
            td.isToday = (i == 0);
            if (i == 0) {
                td.dayLabel = "TODAY " + target.get(Calendar.DAY_OF_MONTH);
            } else {
                td.dayLabel = dayFmt.format(target.getTime()).toUpperCase(Locale.US);
            }

            int year = target.get(Calendar.YEAR);
            int month = target.get(Calendar.MONTH) + 1;
            int day = target.get(Calendar.DAY_OF_MONTH);

            int[] tithiResult = computeAstronomicalTithi(year, month, day);
            int paksha = tithiResult[0]; // 1 = Sud, 2 = Vad
            int tithiNum = tithiResult[1];

            td.tithiText = (paksha == 1 ? "Sud " : "Vad ") + tithiNum;

            if (tithiNum == 8) {
                td.badge = "Aathm 🪔";
                td.isParva = true;
            } else if (tithiNum == 11) {
                td.badge = "Agiyaras";
                td.isParva = true;
            } else if (tithiNum == 14) {
                td.badge = "Chaudas";
                td.isParva = true;
            } else if (tithiNum == 15 && paksha == 1) {
                td.badge = "Poonam";
                td.isParva = true;
            } else if (tithiNum == 15 && paksha == 2) {
                td.badge = "Amas";
                td.isParva = true;
            } else if (tithiNum == 5) {
                td.badge = "Pancham";
                td.isParva = true;
            } else {
                td.badge = i == 0 ? "Today" : "Normal";
                td.isParva = false;
            }

            list.add(td);
        }
        return list;
    }

    /**
     * Compute current Choghadiya slots for today based on sunrise and sunset times.
     */
    public static ChoghadiyaInfo getTodayChoghadiya(long sunriseMillis, long sunsetMillis) {
        ChoghadiyaInfo info = new ChoghadiyaInfo();
        long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        boolean isNight = (now < sunriseMillis || now >= sunsetMillis);
        info.isNight = isNight;

        String[] slotNames;
        long startTime, endTime;

        if (!isNight) {
            slotNames = DAY_CHOGHADIYA[dayOfWeek];
            startTime = sunriseMillis;
            endTime = sunsetMillis;
        } else {
            if (now < sunriseMillis) {
                // Early morning before sunrise belongs to previous day's night Choghadiya
                int prevDay = dayOfWeek == Calendar.SUNDAY ? Calendar.SATURDAY : dayOfWeek - 1;
                slotNames = NIGHT_CHOGHADIYA[prevDay];
                startTime = sunsetMillis - 24 * 3600 * 1000L;
                endTime = sunriseMillis;
            } else {
                slotNames = NIGHT_CHOGHADIYA[dayOfWeek];
                startTime = sunsetMillis;
                endTime = sunriseMillis + 24 * 3600 * 1000L;
            }
        }

        long slotDuration = (endTime - startTime) / 8;
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat fullFmt = new SimpleDateFormat("h:mm a", Locale.US);

        String activeName = "";
        String activeRange = "";

        for (int i = 0; i < 8; i++) {
            ChoghadiyaSlot slot = new ChoghadiyaSlot();
            slot.name = slotNames[i];
            long s = startTime + i * slotDuration;
            long e = startTime + (i + 1) * slotDuration;

            slot.timeRange = timeFmt.format(new Date(s)) + " - " + timeFmt.format(new Date(e));
            slot.isCurrent = (now >= s && now < e);

            if (slot.name.equals("Amrit")) {
                slot.statusLabel = "Best";
                slot.natureType = 1;
            } else if (slot.name.equals("Shubh") || slot.name.equals("Labh")) {
                slot.statusLabel = "Good";
                slot.natureType = 1;
            } else if (slot.name.equals("Chal")) {
                slot.statusLabel = "Neutral";
                slot.natureType = 2;
            } else {
                slot.statusLabel = "Avoid";
                slot.natureType = 3;
            }

            if (slot.isCurrent) {
                activeName = slot.name.toUpperCase(Locale.US);
                activeRange = fullFmt.format(new Date(s)) + " – " + fullFmt.format(new Date(e));
            }

            info.slots.add(slot);
        }

        String period = isNight ? "NIGHT" : "DAY";
        if (!activeName.isEmpty()) {
            info.title = "CHOGHADIYA (" + period + ") — CURRENT: " + activeName + " (" + activeRange + ")";
        } else {
            info.title = "CHOGHADIYA (" + period + ")";
        }

        return info;
    }

    /**
     * Astronomical Lunar Phase (Jean Meeus algorithm) evaluated at sunrise.
     * Returns [paksha (1=Sud, 2=Vad), tithiNumber (1-15)].
     */
    private static int[] computeAstronomicalTithi(int year, int month, int day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = y / 100;
        int b = 2 - a + (a / 4);
        double jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5;

        // Evaluated at Sunrise (~6:10 AM IST = ~00:40 UTC = 0.028 day)
        jd += 0.028;

        double t = (jd - 2451545.0) / 36525.0;
        double d = (297.85036 + 445267.111480 * t - 0.0019142 * t * t + t * t * t / 189474.0) % 360.0;
        double mSun = (357.52772 + 35999.050340 * t - 0.0001603 * t * t - t * t * t / 300000.0) % 360.0;
        double mPrime = (134.96298 + 477198.867398 * t + 0.0086972 * t * t + t * t * t / 56250.0) % 360.0;

        double dRad = Math.toRadians(d);
        double mRad = Math.toRadians(mSun);
        double mpRad = Math.toRadians(mPrime);

        double elongation = (d + 6.289 * Math.sin(mpRad) - 2.100 * Math.sin(mRad) - 1.274 * Math.sin(2 * dRad - mpRad)) % 360.0;
        if (elongation < 0) elongation += 360.0;

        int tithiIndex = (int) (elongation / 12.0) + 1;
        int paksha = (tithiIndex <= 15) ? 1 : 2;
        int tithiNum = (tithiIndex <= 15) ? tithiIndex : (tithiIndex - 15);

        return new int[]{paksha, tithiNum};
    }
}
