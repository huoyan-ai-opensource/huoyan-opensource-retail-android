package cc.huoyan.retail.device.print;

import android.graphics.Bitmap;
import android.text.TextPaint;
import android.graphics.Typeface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.domain.WeightedSaleResult;
import cc.huoyan.retail.pricing.PricingMode;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用真实字体和位图渲染核对重量字段；不连接打印机、不提交订单。 */
@RunWith(AndroidJUnit4.class)
public class WeightedReceiptDeviceTest {
    /** 长商品名不能覆盖右侧重量；称重行不再输出个数。 */
    @Test public void weightedRowShowsWeightWithoutOverlap() throws Exception {
        WeightedSaleResult result=new WeightedSaleResult("receipt-fixture",new BigDecimal("1.250"),2000,2500,1,2,
                Collections.singletonList(new WeightedSaleResult.Item("12345678","称重商品长名称显示检查",2000,2,false,null,PricingMode.WEIGHT)));
        ReceiptRenderer renderer=new ReceiptRenderer();
        Method method=ReceiptRenderer.class.getDeclaredMethod("buildReceiptHeader",WeightedSaleResult.class);method.setAccessible(true);
        List<Line> lines=(List<Line>)method.invoke(renderer,result);
        List<Unit> units=lines.get(lines.size()-1).getUnits();
        assertEquals("1.250 kg",units.get(1).getContent());
        TextPaint paint=new TextPaint();paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(18);
        assertTrue(paint.measureText(units.get(0).getContent())+paint.measureText(units.get(1).getContent())+10<=ReceiptPaper.CONTENT_WIDTH);
        Bitmap bitmap=renderer.header(result);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"integration-review/weighted-receipt.png");
        file.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
}
