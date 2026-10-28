package cc.huoyan.retail.common;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionUtil {

    public static final String[] PERMISSIONS_STORAGE = {
            permission.READ_EXTERNAL_STORAGE,
            permission.WRITE_EXTERNAL_STORAGE
    };

    public static final String[] PERMISSIONS_LOCATION = {
            permission.ACCESS_FINE_LOCATION,
            permission.ACCESS_COARSE_LOCATION
    };

    public static final String[] BASE_NEED_PERMISSIONS = {
            permission.CAMERA,
            permission.WRITE_EXTERNAL_STORAGE,
            permission.READ_EXTERNAL_STORAGE
    };

    public static final int REQUEST_EXTERNAL_STORAGE = 1001;

    public static final int REQUEST_GEOLOCATION = 1002;
    public static final int REQUEST_BASE_PERMISSION = 1003;
    public static final int REQUEST_INSTALL_PERMISSION = 1004;


    public static boolean verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            return false;
        }
        return true;
    }

    public static boolean verifyLocationPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_LOCATION, REQUEST_GEOLOCATION);
            return false;
        }
        return true;
    }

    public static boolean verifyBasePermissions(Activity activity) {
        if (!hasPermissions(activity, BASE_NEED_PERMISSIONS)) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, BASE_NEED_PERMISSIONS, REQUEST_BASE_PERMISSION);
            return false;
        }
        return true;
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
