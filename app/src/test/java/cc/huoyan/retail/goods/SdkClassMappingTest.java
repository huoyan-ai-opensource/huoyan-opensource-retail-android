package cc.huoyan.retail.goods;
import org.junit.Test;
import static org.junit.Assert.*;
public class SdkClassMappingTest {
    @Test public void preservesExactAlphanumericPlu() {
        for (String plu : new String[]{"A001", "a001", "001", "0", "9223372036854775808", "A".repeat(50)})
            assertEquals(plu, SdkClassMapping.clazzFromPlu(plu));
    }
    @Test public void rejectsInvalidCharactersAndLength() {
        for (String plu : new String[]{null, "", " A1", "A1 ", "A-1", "A_1", "商品1", "A".repeat(51)})
            assertNull(SdkClassMapping.clazzFromPlu(plu));
    }
}
