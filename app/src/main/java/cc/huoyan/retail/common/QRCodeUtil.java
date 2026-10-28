package cc.huoyan.retail.common;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Pattern;

public class QRCodeUtil {
    public static String getSystemDay(int time) {
        long timestamp1 = System.currentTimeMillis() + 86400L * 1000 * time;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);// HH:mm:ss
        //获取当前时间
        Date date = new Date(timestamp1);
        return simpleDateFormat.format(date);
    }

    public static String getProductionDay(String format) {
        long timestamp1 = System.currentTimeMillis();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format, Locale.CHINA);// HH:mm:ss
        //获取当前时间
        Date date = new Date(timestamp1);
        return simpleDateFormat.format(date);
    }


    public static boolean isInteger(String str) {
        return Pattern.compile("^-?\\d+$").matcher(str).matches();
    }

    public static String countExpDate(int daysToAdd) {
        // 获取当前日期
        Calendar calendar = Calendar.getInstance();
        // 加上指定的天数
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
        Date newDate = calendar.getTime();

        // 按照指定格式输出日期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
        return sdf.format(newDate);
    }

    public static Bitmap createQRCodeBitmap(String content, int width, int height) throws WriterException {
        // 设置二维码的编码格式和参数
        Hashtable<EncodeHintType, String> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, "H");
        hints.put(EncodeHintType.MARGIN, "2");

        // 生成二维码的位矩阵
        BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        // 创建像素数组，并根据位矩阵为数组元素赋颜色值
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitMatrix.get(x, y)) {
                    pixels[y * width + x] = Color.BLACK; // 黑色色块
                } else {
                    pixels[y * width + x] = Color.WHITE; // 白色色块
                }
            }
        }

        // 创建Bitmap对象，并返回
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
