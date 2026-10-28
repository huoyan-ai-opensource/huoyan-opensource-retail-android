package cc.huoyan.retail.learning;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 注入授权回执，验证保存入口；不修改设备证书或提交学习。 */
@RunWith(AndroidJUnit4.class)
public class StudyAvailabilityDeviceTest {
    /** 未激活默认禁用，激活可用后启用，失效后再次禁用。 */
    @Test public void saveTracksCurrentActivation() throws Exception {
        Instrumentation ins=InstrumentationRegistry.getInstrumentation();
        SdkChannelManager manager=SdkChannelManager.getInstance();
        HuoyanSdkClient real=manager.getClient();
        Field sessionField=SdkChannelManager.class.getDeclaredField("session"); sessionField.setAccessible(true);
        Object session=sessionField.get(manager);
        Field clientField=session.getClass().getDeclaredField("client"); clientField.setAccessible(true);
        AtomicBoolean ready=new AtomicBoolean(false);
        HuoyanSdkClient fake=(HuoyanSdkClient)Proxy.newProxyInstance(real.getClass().getClassLoader(),new Class[]{HuoyanSdkClient.class},(proxy,method,args)->{
            if(method.getName().equals("isConnected")) return true;
            if(method.getName().equals("getStatus")) return new StatusSnapshot(0,null,null,null,null,null,false,true,
                    ready.get()?"READY":"NOT_ACTIVATED",ready.get(),false,null,1L,ready.get());
            try { return method.invoke(real,args); } catch(InvocationTargetException error) { throw error.getCause(); }
        });
        try {
            synchronized(session) { clientField.set(session,fake); }
            try(ActivityScenario<ProductLearningActivity> scene=ActivityScenario.launch(new Intent(ins.getTargetContext(),ProductLearningActivity.class))) {
                SystemClock.sleep(1200);
                scene.onActivity(a->assertFalse(a.findViewById(R.id.saveStudy).isEnabled()));
                ready.set(true); SystemClock.sleep(1500);
                scene.onActivity(a->assertTrue(a.findViewById(R.id.saveStudy).isEnabled()));
                ready.set(false); SystemClock.sleep(1500);
                scene.onActivity(a->assertFalse(a.findViewById(R.id.saveStudy).isEnabled()));
            }
        } finally { synchronized(session) { clientField.set(session,real); } }
    }
}
