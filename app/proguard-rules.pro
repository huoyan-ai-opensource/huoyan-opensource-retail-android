# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# ========== Gson (JSON 序列化) ==========
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
  @com.google.gson.annotations.Expose <fields>;
}

# 保留迁移到各业务包中的原实体类，维持 Gson 字段名。
-keep class cc.huoyan.retail.checkout.GoodsVo { *; }
-keep class cc.huoyan.retail.checkout.PointGoodsEntity { *; }
-keep class cc.huoyan.retail.checkout.RecognizeModifyCandidate { *; }
-keep class cc.huoyan.retail.checkout.domain.WeightedSaleResult { *; }
-keep class cc.huoyan.retail.checkout.domain.WeightedSaleResult$* { *; }
-keep class cc.huoyan.retail.common.EventBusModel { *; }
-keep class cc.huoyan.retail.data.GoodsEntity { *; }
-keep class cc.huoyan.retail.device.camera.CameraConfigEntity { *; }
-keep class cc.huoyan.retail.device.camera.RecognizeAreaEntity { *; }
-keep class cc.huoyan.retail.device.scale.AclasWeightSnapshot { *; }
-keep class cc.huoyan.retail.diagnostics.LogFIleDirectory { *; }
-keep class cc.huoyan.retail.diagnostics.LogFIleDirectoryType { *; }
-keep class cc.huoyan.retail.diagnostics.SdkLogEntity { *; }
-keep class cc.huoyan.retail.learning.StudyPicEntity { *; }
-keep class cc.huoyan.retail.settings.ScanSerialEntity { *; }
-keep class cc.huoyan.retail.device.camera.CameraUsbEntity { *; }

# ========== xCrash (崩溃上报) ==========
-keep class com.iqiyi.xcrash.** { *; }
-dontwarn com.iqiyi.xcrash.**

# ========== UVCAndroid (USB 摄像头) ==========
-keep class com.herohan.uvcapp.** { *; }
-keep class com.serenegiant.usb.** { *; }
-dontwarn com.serenegiant.usb.**

# ========== JNI Native 方法 ==========
-keep class cc.huoyan.retail.ThreadAffinity {
    native <methods>;
}

# ========== AndroidX ==========
-keep class androidx.** { *; }
-dontwarn androidx.**

# ========== Android 组件 ==========
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# ========== 枚举 ==========
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== Parcelable ==========
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# ========== Serializable ==========
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== 保留本地库 ==========
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== 移除日志 ==========
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
