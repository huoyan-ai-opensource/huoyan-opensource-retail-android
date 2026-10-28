package cc.huoyan.retail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** 验证新包名的直接页面入口和不同资源限定符下的自定义控件类引用。 */
@RunWith(AndroidJUnit4.class)
public class PackageStructureDeviceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    /** 在设备上检查首页与收银入口，防止迁包后仍跳转到旧组件别名。 */
    @Test public void launcherAndCheckoutResolveToConcreteActivities() throws Exception {
        String pkg = context.getPackageName();
        assertEquals("cc.huoyan.retail", pkg);
        PackageManager manager = context.getPackageManager();
        for (String entry : new String[] {"home.HomeActivity", "checkout.MainActivity"}) {
            ActivityInfo target = manager.getActivityInfo(new ComponentName(pkg, pkg + "." + entry), 0);
            assertTrue(target.exported);
            assertNull(target.targetActivity);
            assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, target.launchMode);
            assertNotNull(Class.forName(target.name, false, context.getClassLoader()));
        }
        Intent launch = manager.getLaunchIntentForPackage(pkg);
        assertNotNull(launch);
        assertEquals(pkg + ".home.HomeActivity", launch.getComponent().getClassName());
    }

    @Test public void customViewsResolveInPortraitAndLandscapeResources() throws Exception {
        int customViews = 0;
        for (int width : new int[] {360, 720}) {
            for (int orientation : new int[] {Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE}) {
                Configuration config = new Configuration(context.getResources().getConfiguration());
                config.orientation = orientation;
                config.smallestScreenWidthDp = width;
                Context configured = context.createConfigurationContext(config);
                for (Field layout : R.layout.class.getFields()) {
                    try (XmlResourceParser xml = configured.getResources().getLayout(layout.getInt(null))) {
                        int event;
                        while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
                            if (event != XmlPullParser.START_TAG) continue;
                            String name = xml.getName();
                            if ("view".equals(name)) name = xml.getAttributeValue(null, "class");
                            if (name == null || !name.startsWith(context.getPackageName() + ".")) continue;
                            Class<?> type = Class.forName(name, false, context.getClassLoader());
                            assertTrue(layout.getName() + ": " + name, View.class.isAssignableFrom(type));
                            customViews++;
                        }
                    }
                }
            }
        }
        assertTrue("需要覆盖应用自定义控件", customViews > 0);
    }
}
