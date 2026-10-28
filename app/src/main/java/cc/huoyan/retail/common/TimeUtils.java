package cc.huoyan.retail.common;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Time;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


/**
 * Created by feng
 * Created on 2020/8/14
 */
public class TimeUtils {

    private static final ThreadLocal<Map<String, SimpleDateFormat>> SDF_THREAD_LOCAL
            = new ThreadLocal<Map<String, SimpleDateFormat>>() {
        @Override
        protected Map<String, SimpleDateFormat> initialValue() {
            return new HashMap<>();
        }
    };

    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000;

    /**
     * 将时间转换为时间戳
     *
     * @return
     */
    public static long dateToStamp(String time) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date date = null;
        try {
            date = simpleDateFormat.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date.getTime();
    }

    public static long dateToStamp(String s, String pattern) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.getDefault());
        Date date = null;
        try {
            date = simpleDateFormat.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long ts = date.getTime();
        return date.getTime();
    }

    /**
     * 将时间字符串转换为时间戳
     *
     * @return milliseconds 毫秒格式
     */
    public static long dateToTimeStamp(String time) {
        if (TextUtils.isEmpty(time)) return 0;
        String pattern = time.contains("T") ? "yyyy-MM-dd'T'HH:mm:ss" : "yyyy-MM-dd HH:mm:ss";

        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.getDefault());
            Date date = simpleDateFormat.parse(time);
            return date != null ? date.getTime() : 0L;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    /**
     * 格式化传入的时间
     *
     * @param time 需要格式化的时间
     * @return
     */
    public static String getReqTime(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date d1 = new Date(time);
        return format.format(d1);
    }

    /**
     * 格式化传入的时间
     * " yyyy-MM-dd HH:mm"
     *
     * @param time      需要格式化的时间
     * @param formatStr 格式化的格式
     * @return
     */
    public static String getCustomFormatTime(long time, String formatStr) {
        SimpleDateFormat format = new SimpleDateFormat(formatStr, Locale.getDefault());
        Date d1 = new Date(time);
        return format.format(d1);
    }

    /**
     * 格式化传入的时间
     *
     * @param time 需要格式化的时间
     * @param num  天数
     * @return
     */
    public static String addDay(long time, int num) {
        //获取Calendar的实例，默认是当前的时间
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(time));
        //当前时间 加x天
        calendar.add(Calendar.DAY_OF_YEAR, num);
        //new SimgpleDateFormat 进行格式化
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        //利用Calendar的getTime方法，将时间转化为Date对象
        Date date = calendar.getTime();
        //利用SimpleDateFormat对象 把Date对象格式化
        return sdf.format(date);
    }

    /**
     * 计算两个日期之间的日期
     *
     * @param startTime
     * @param endTime
     */
    public static List<String> betweenDays(long startTime, long endTime) {
        List<String> timeList = new ArrayList<>();
        Date date_start = new Date(startTime);
        Date date_end = new Date(endTime);

        //计算日期从开始时间于结束时间的0时计算
        Calendar fromCalendar = Calendar.getInstance();
        fromCalendar.setTime(date_start);
        fromCalendar.set(Calendar.HOUR_OF_DAY, 0);
        fromCalendar.set(Calendar.MINUTE, 0);
        fromCalendar.set(Calendar.SECOND, 0);
        fromCalendar.set(Calendar.MILLISECOND, 0);

        Calendar toCalendar = Calendar.getInstance();
        toCalendar.setTime(date_end);
        toCalendar.set(Calendar.HOUR_OF_DAY, 0);
        toCalendar.set(Calendar.MINUTE, 0);
        toCalendar.set(Calendar.SECOND, 0);
        toCalendar.set(Calendar.MILLISECOND, 0);

        int s = (int) ((toCalendar.getTimeInMillis() - fromCalendar.getTimeInMillis()) / (ONE_DAY_MS));
        if (s > 0) {
            for (int i = 0; i <= s; i++) {
                long todayDate = fromCalendar.getTimeInMillis() + i * ONE_DAY_MS;
                /**
                 * yyyy-MM-dd E :2012-09-01
                 */
                timeList.add(getCustomFormatTime(todayDate, "yyyy-MM-dd"));
                //取出多少号  添加集合  九宫格展示


            }
        } else {//此时在同一天之内
            timeList.add(getCustomFormatTime(startTime, "yyyy-MM-dd"));
        }
        return timeList;
    }

    /**
     * 判断2个时间大小
     * yyyy-MM-dd HH:mm 格式（自己可以修改成想要的时间格式）
     *
     * @param startTime
     * @param endTime
     * @return
     */
    public static int timeCompare(String startTime, String endTime) {
        int i = 0;
        //注意：传过来的时间格式必须要和这里填入的时间格式相同
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date date1 = dateFormat.parse(startTime);//开始时间
            Date date2 = dateFormat.parse(endTime);//结束时间
            // 1 结束时间小于开始时间 2 开始时间与结束时间相同 3 结束时间大于开始时间
            if (date2.getTime() < date1.getTime()) {
                //结束时间小于开始时间
                i = 1;
            } else if (date2.getTime() == date1.getTime()) {
                //开始时间与结束时间相同
                i = 2;
            } else if (date2.getTime() > date1.getTime()) {
                //结束时间大于开始时间
                i = 3;
            }
        } catch (Exception e) {

        }
        return i;
    }

    /**
     * 初始化Calendar,设置相关信息为中国时间数据
     */
    public static Calendar getCalendarByChina() {
        Calendar cal = Calendar.getInstance();//这一句必须要设置，否则美国认为第一天是周日，而我国认为是周一，对计算当期日期是第几周会有错误
        cal.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        cal.setFirstDayOfWeek(Calendar.MONDAY); // 设置每周的第一天为星期一
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);// 每周从周一开始
        cal.setMinimalDaysInFirstWeek(7); // 设置每周最少为7天
        cal.setTime(new Date());
        return cal;
    }

    /**
     * 获取当天是周几
     * <p>
     * PS:
     */
    public static int getDayOfWeek() {
        Calendar cal = Calendar.getInstance();//这一句必须要设置，否则美国认为第一天是周日，而我国认为是周一，对计算当期日期是第几周会有错误
        cal.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        cal.setFirstDayOfWeek(Calendar.MONDAY); // 设置每周的第一天为星期一
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);// 每周从周一开始
        cal.setMinimalDaysInFirstWeek(7); // 设置每周最少为7天
        cal.setTime(new Date());
        return cal.get(Calendar.DAY_OF_WEEK);
    }


    public static int getWeek() {
        Calendar cal = Calendar.getInstance();//这一句必须要设置，否则美国认为第一天是周日，而我国认为是周一，对计算当期日期是第几周会有错误
        cal.setFirstDayOfWeek(Calendar.MONDAY); // 设置每周的第一天为星期一
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);// 每周从周一开始
        cal.setMinimalDaysInFirstWeek(7); // 设置每周最少为7天
        cal.setTime(new Date());
        return cal.get(Calendar.WEEK_OF_YEAR);
    }

    public static String getYearMonth() {
        Calendar cal = Calendar.getInstance();//这一句必须要设置，否则美国认为第一天是周日，而我国认为是周一，对计算当期日期是第几周会有错误
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);// 每周从周一开始
        cal.setMinimalDaysInFirstWeek(7); // 设置每周最少为7天
        cal.setTime(new Date());
        return year + "-" + month;
    }


    public static String getStartEndOFWeek(int enterWeek, int enterYear) {
        //enterWeek is week number
        //enterYear is year
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.WEEK_OF_YEAR, enterWeek);
        calendar.set(Calendar.YEAR, enterYear);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()); // PST`
        Date startDate = calendar.getTime();
        return formatter.format(startDate);

        //        calendar.add(Calendar.DATE, 6);
        //        Date enddate = calendar.getTime();
        //        String endDaString = formatter.format(enddate);
        //        System.out.println("...date..." + endDaString);
    }

    /**
     * 获取当前时间,使用于同步到云端的模型
     *
     * @return
     */
    public static String getCurrentTime() {
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        SimpleDateFormat formatter_year = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return formatter_year.format(curDate);
    }

    public static int getYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public static int getMonth() {
        return Calendar.getInstance().get(Calendar.MONTH) + 1;
    }

    public static int getDay() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取当月时间
     *
     * @return
     */
    public static String getCurrentTimeMonth() {
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        SimpleDateFormat formatter_year = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        return formatter_year.format(curDate);
    }

    /**
     * 获取今天的时间戳
     * <p>
     * day=0,今天
     * day =-1,昨天 以此类推
     *
     * @return
     */
    public static long getToday(int dayNum) {
        long timeLong;
        Time t = new Time("GMT+8");
        t.setToNow(); // 取得系统时间。
        int year = t.year;
        int month = t.month + 1;
        int day = t.monthDay + dayNum;
        timeLong = dateToStamp(year + "-" + month + "-" + day);
        return timeLong;
    }

    public static String getTodayStr(int dayNum) {
        long timeLong;
        Time t = new Time("GMT+8");
        t.setToNow(); // 取得系统时间。
        int year = t.year;
        int month = t.month + 1;
        int day = t.monthDay + dayNum;
        timeLong = dateToStamp(year + "-" + month + "-" + day);
        return getCustomFormatTime(timeLong, "yyyy-MM-dd");
    }

    /**
     * 获取本月的时间戳
     * <p>
     * monthNum=0,本月
     * monthNum =-1,上个月 以此类推
     *
     * @return
     */
    public static long getMonth(int monthNum) throws ParseException {
        long timeLong = 0;
        Time t = new Time("GMT+8");
        t.setToNow(); // 取得系统时间。
        int year = t.year;
        int month = t.month + 1 + monthNum;
        int day = t.monthDay;
        timeLong = dateToStamp(year + "-" + month + "-" + day);
        return timeLong;
    }


    /**
     * 获取指定某一天的开始时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getDailyStartTime(Long timeStamp, String timeZone) {
        if (timeZone == null) timeZone = "GMT+8:00";
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取指定某一天的开始时间戳
     *
     * @param time     日期
     * @param timeZone 如 GMT+8:00
     * @return
     */
    public static Long getDateToStartTime(String time, String timeZone) {
        long timeLong = dateToStamp(time);
        if (timeZone == null) timeZone = "GMT+8:00";
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeLong);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }


    /**
     * 获取指定某一天的结束时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getDailyEndTime(Long timeStamp, String timeZone) {
        if (timeZone == null) timeZone = "GMT+8:00";
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取指定某一天的结束时间戳
     *
     * @param time     日期
     * @param timeZone 如 GMT+8:00
     * @return
     */
    public static Long getDateToEndTime(String time, String timeZone) {
        long timeLong = dateToStamp(time);
        if (timeZone == null) timeZone = "GMT+8:00";
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeLong);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }


    /**
     * 获取本周开始时间戳
     */
    public static long getTimeOfWeekStart() {
        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.HOUR_OF_DAY, 0);
        ca.clear(Calendar.MINUTE);
        ca.clear(Calendar.SECOND);
        ca.clear(Calendar.MILLISECOND);
        ca.set(Calendar.DAY_OF_WEEK, ca.getFirstDayOfWeek());
        return ca.getTimeInMillis();
    }

    /**
     * 获取当月开始时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getMonthStartTime(Long timeStamp, String timeZone) {
        Calendar calendar = Calendar.getInstance();// 获取当前日期
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        calendar.add(Calendar.YEAR, 0);
        calendar.add(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);// 设置为1号,当前日期既为本月第一天
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取当月的结束时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getMonthEndTime(Long timeStamp, String timeZone) {
        Calendar calendar = Calendar.getInstance();// 获取当前日期
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        calendar.add(Calendar.YEAR, 0);
        calendar.add(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));// 获取当前月最后一天
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取当年的开始时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getYearStartTime(Long timeStamp, String timeZone) {
        Calendar calendar = Calendar.getInstance();// 获取当前日期
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        calendar.add(Calendar.YEAR, 0);
        calendar.add(Calendar.DATE, 0);
        calendar.add(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取当年的最后时间戳
     *
     * @param timeStamp 毫秒级时间戳
     * @param timeZone  如 GMT+8:00
     * @return
     */
    public static Long getYearEndTime(Long timeStamp, String timeZone) {
        Calendar calendar = Calendar.getInstance();// 获取当前日期
        calendar.setTimeZone(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(timeStamp);
        int year = calendar.get(Calendar.YEAR);
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        calendar.roll(Calendar.DAY_OF_YEAR, -1);
        return calendar.getTimeInMillis();
    }

    /**
     * 时间戳转字符串
     *
     * @param timestamp 毫秒级时间戳
     * @param zoneId    如 GMT+8或UTC+08:00
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String timestampToStr(long timestamp, String zoneId) {
        ZoneId timezone = ZoneId.of(zoneId);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timezone);
        return localDateTime.toString();
    }

//    /**
//     * 将时间选择器的CalendarDay类型转为String格式的起始日期
//     *
//     * @return
//     */
//    public static String calendarDayToStartDate(CalendarDay startCalendar) {
//        String data = startCalendar.getYear() + "-" + startCalendar.getMonth() + "-" + startCalendar.getDay();
//        Long dataLong = TimeUtils.getDateToStartTime(data, null);
//        return getCustomFormatTime(dataLong, "yyyyMMdd");
//    }
//
//    /**
//     * 将时间选择器的CalendarDay类型转为时间戳
//     *
//     * @return
//     */
//    public static long calendarDayToStartLong(CalendarDay startCalendar) {
//        String data = startCalendar.getYear() + "-" + (startCalendar.getMonth() + 1) + "-" + startCalendar.getDay();
//        Long dataLong = TimeUtils.getDateToStartTime(data, null);
//        return dataLong;
//    }

    /**
     * 将时间选择器的CalendarDay类型转为String格式的起始日期
     *
     * @return
     */
    public static String calendarDayToStartDate(Long timeLong) {
        return getCustomFormatTime(timeLong, "yyyyMMdd");
    }

    /**
     * 将时间选择器的CalendarDay类型转为String格式的结束日期
     *
     * @return
     */
//    public static String calendarDayToEndDate(CalendarDay endCalendar) {
//        String data = endCalendar.getYear() + "-" + endCalendar.getMonth() + "-" + endCalendar.getDay();
//        Long dataLong = TimeUtils.getDateToStartTime(data, null);
//        return getCustomFormatTime(dataLong, "yyyyMMdd");
//    }

    /**
     * 每天的开始时间
     */
    public static long getStartOfInMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }


    /**
     * 每天的开始时间
     */
    public static String getStartOfDayInMillis() {
        long startOfInMillis = getStartOfInMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(sdf);
    }

    /**
     * 现在的时间
     */
    public static long getEndOfInMillis() {
        // Add one day's time to the beginning of the day.
        // 24 hours * 60 minutes * 60 seconds * 1000 milliseconds = 1 day
//        return getStartOfDayInMillis() + (24 * 60 * 60 * 1000);
        return getStartOfInMillis() + System.currentTimeMillis();
    }

    /**
     * 现在的时间
     */
    public static String getEndOfDayInMillis() {
        // Add one day's time to the beginning of the day.
        // 24 hours * 60 minutes * 60 seconds * 1000 milliseconds = 1 day
//        return getStartOfDayInMillis() + (24 * 60 * 60 * 1000);
        long endOfInMillis = getEndOfInMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(endOfInMillis);
    }


    /**
     * 对比传递来的时间和当前时间是否有五分钟差距
     * true表示大于五分钟了  反之五分钟内
     */
    public static boolean comparisonStamp(long syncTime) {
        long nowTime = System.currentTimeMillis() / 1000;
        //五分钟
        return nowTime - syncTime > 300;
    }

    @SuppressLint("SimpleDateFormat")
    public static SimpleDateFormat getSafeDateFormat(String pattern) {
        Map<String, SimpleDateFormat> sdfMap = SDF_THREAD_LOCAL.get();
        //noinspection ConstantConditions
        SimpleDateFormat simpleDateFormat = sdfMap.get(pattern);
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat(pattern);
            sdfMap.put(pattern, simpleDateFormat);
        }
        return simpleDateFormat;
    }

    public static String ymd2String(long millis) {
        return millis2String(millis, getSafeDateFormat("yyyyMMdd"));
    }

    public static String millis2String(long millis, @NonNull final String pattern) {
        return millis2String(millis, getSafeDateFormat(pattern));
    }

    public static String millis2String(final long millis, @NonNull final DateFormat format) {
        return format.format(new Date(millis));
    }

    /**
     * 系统时间和time做比较，是否在同一周
     *
     * @param time 毫秒级别时间戳
     * @return
     */
    public static boolean isSameWeek(long time) {
        Calendar calendarByChina = getCalendarByChina();
        Integer year1 = calendarByChina.get(calendarByChina.YEAR);
        Integer week1 = calendarByChina.get(calendarByChina.WEEK_OF_YEAR);

        calendarByChina.setTimeInMillis(time);
        Integer year2 = calendarByChina.get(calendarByChina.YEAR);
        Integer week2 = calendarByChina.get(calendarByChina.WEEK_OF_YEAR);

        if (year1.intValue() == year2.intValue() && week1.intValue() == week2.intValue())
            return true;
        return false;
    }


    /**
     * 系统时间和time做比较，是否在同一月
     *
     * @param time 毫秒级别时间戳
     * @return
     */
    public static boolean isSameMonth(long time) {
        Calendar calendarByChina = getCalendarByChina();
        Integer year1 = calendarByChina.get(calendarByChina.YEAR);
        Integer month1 = calendarByChina.get(calendarByChina.MONTH);

        calendarByChina.setTimeInMillis(time);
        Integer year2 = calendarByChina.get(calendarByChina.YEAR);
        Integer month2 = calendarByChina.get(calendarByChina.MONTH);

        if (year1.intValue() == year2.intValue() && month1.intValue() == month2.intValue())
            return true;
        return false;
    }

    /**
     * 系统时间和time做比较，是否在同一年
     *
     * @param time 毫秒级别时间戳
     * @return
     */
    public static boolean isSameYear(long time) {
        Calendar calendarByChina = getCalendarByChina();
        Integer year1 = calendarByChina.get(calendarByChina.YEAR);

        calendarByChina.setTimeInMillis(time);
        Integer year2 = calendarByChina.get(calendarByChina.YEAR);

        if (year1.intValue() == year2.intValue()) {
            return true;
        } else {
            return false;
        }
    }

}
