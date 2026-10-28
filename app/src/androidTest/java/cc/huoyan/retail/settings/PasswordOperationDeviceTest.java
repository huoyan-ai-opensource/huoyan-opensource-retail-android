package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 独立测试偏好验证密码计算线程，不读取或修改用户退出密码。 */
@RunWith(AndroidJUnit4.class)
public class PasswordOperationDeviceTest {
    /** 真机执行派生，主线程提交不等待计算或持久化。 */
    @Test public void passwordWorkDoesNotBlockMainThread() {
        Instrumentation ins=InstrumentationRegistry.getInstrumentation();
        Context base=ins.getTargetContext();
        String file="password-operation-test-"+SystemClock.elapsedRealtime();
        Context isolated=new ContextWrapper(base) {
            /** 派生操作只使用隔离偏好上下文。 */
            @Override public Context getApplicationContext(){return this;}
            /** 将测试密码数据固定路由至本次专用文件。 */
            @Override public SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(file,mode);}
        };
        ExitPasswordDialog.Operation operation=new ExitPasswordDialog.Operation();
        try {
            assertTrue(ExitPasswordStore.save(isolated,"314159",true));
            ins.runOnMainSync(()->{
                long start=SystemClock.elapsedRealtime();operation.submit(isolated,"314159",false,false);
                assertTrue("提交不能等待 PBKDF2",SystemClock.elapsedRealtime()-start<100);
            });
            AtomicReference<Boolean> result=new AtomicReference<>();
            long deadline=SystemClock.elapsedRealtime()+10000;
            do { SystemClock.sleep(50);ins.runOnMainSync(()->result.set(operation.result.getValue())); }
            while(result.get()==null && SystemClock.elapsedRealtime()<deadline);
            assertEquals(Boolean.TRUE,result.get());
        } finally { operation.onCleared();base.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit(); }
    }
}
