package cc.huoyan.retail.common;

import com.google.gson.Gson;

import java.lang.reflect.Type;

public class GsonUtil {
    private static final Gson gson = new Gson();

    public static <T> T fromJson(String json, Class<T> classz) {
        return gson.fromJson(json, classz);
    }

    public static <T> T fromJson(String json, Type type) {
        return gson.fromJson(json, type);
    }

    public static <T> String toJson(T entity) {
        return gson.toJson(entity);
    }

}
