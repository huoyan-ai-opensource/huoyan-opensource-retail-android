package cc.huoyan.retail.device.print;

public class EAN13Util {

    public static int calculateENA13Checksum(String enaNumber) {
        if (enaNumber == null || enaNumber.length() != 13) {
            throw new IllegalArgumentException("ENA number should be 13 characters long");
        }

        int sum = 0;
        int sum_ou = 0;
        int sum_ji = 0;
        for (int i = 0; i < 12; i++) {
            char digit = enaNumber.charAt(i);

            if (!Character.isDigit(digit)) {
                throw new IllegalArgumentException("ENA number should only contain digits");
            }
            //1.自右向左顺序编号690123456789
            //2.从序号2开始求出偶数位上数字之和①
            //   9+7+5+3+1+9＝34 ①
            //3. ①*3=②
            //   34×3＝102 ②
            //4.从序号3开始求出奇数位上数字之和③
            //   8+6+4+2+0+6＝26 ③
            //5. ②+③=④
            //   102+26＝128 ④
            //6.用大于或等于结果④且为10最小整数倍的数减去④，其差即为所求校验码的值
            //   130－128＝2
            //校验码X1＝2
            if ((i + 1) % 2 == 0) {
                sum_ou += Integer.valueOf(enaNumber.charAt(i) + "");
            } else {
                sum_ji += Integer.valueOf(enaNumber.charAt(i) + "");
            }

        }
        sum = sum_ou * 3 + sum_ji;
        sum = (sum % 10 == 0 ? 0 : (10 - sum % 10));

        return sum;

    }
}
