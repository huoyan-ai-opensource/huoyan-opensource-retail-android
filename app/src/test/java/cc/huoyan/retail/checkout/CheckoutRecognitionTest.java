package cc.huoyan.retail.checkout;
import cc.huoyan.sdk.client.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
/** 收银改品不能改写 SDK 回执或丢失候选信息。 */
public class CheckoutRecognitionTest {
    @Test public void editingPreservesOriginalReceiptAndCandidatePrecision() {
        RecognitionCandidate candidate = new RecognitionCandidate("2", .123456789, 42.5, -3.2);
        RecognitionItem item = new RecognitionItem("1", .987654321, 10, 20, 40, 80, 99, -2,
                Collections.singletonList(candidate));
        RecognizeResult result = new RecognizeResult(0, null, Collections.singletonList(item));
        CheckoutRecognition frame = new CheckoutRecognition(result);
        RecognitionSelection selection = frame.getSelections().get(0);
        selection.select("2");
        assertSame(result, frame.getResult()); assertSame(item, selection.getOriginal());
        assertEquals("2", selection.getSelectedPlu()); assertEquals("1", selection.getOriginalClazz());
        assertSame(candidate, selection.getCandidates().get(0));
        assertEquals(-3.2, selection.getCandidates().get(0).getRank(), 0);
        assertEquals(.987654321, selection.getDistance(), 0);
        float[] points = selection.getPoints(); points[0] = 999;
        assertArrayEquals(new float[]{10,20,40,80}, selection.getPoints(), 0);
    }
    @Test public void stableKeyRetainsMultiplicityAndIgnoresEditing() {
        RecognitionItem a = new RecognitionItem("2", 0, 0, 0, 10, 10);
        RecognitionItem b = new RecognitionItem("1", 0, 0, 0, 10, 10);
        CheckoutRecognition frame = new CheckoutRecognition(new RecognizeResult(0, null, Arrays.asList(a,b,a)));
        frame.getSelections().get(0).select("9");
        assertEquals("1,2,2", frame.setKey());
        assertNull(new CheckoutRecognition(new RecognizeResult(0,null,Collections.emptyList())).setKey());
    }
}
