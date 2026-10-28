package cc.huoyan.retail.goods;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileExpiringMap {
    private static final Map<String, Entry> map = new ConcurrentHashMap<>();
    private volatile static FileExpiringMap mInstance;

    public FileExpiringMap() {
    }

    public static FileExpiringMap getInstance() {
        if (mInstance == null) {
            synchronized (FileExpiringMap.class) {
                if (mInstance == null) {
                    mInstance = new FileExpiringMap();
                }
            }
        }
        return mInstance;
    }

    public static void remove(String key) {
        map.remove(key);
    }

    public void put(String key, String value) {
        map.put(key, new Entry(value, System.currentTimeMillis() + 3 * 60 * 60));
    }

    public void expireKey(String key) {
        Entry entry = map.get(key);
        if (entry != null) {
            entry.setExpirationTime(0);
        }
    }

    public Entry get(String key) {
        return map.get(key);
    }

    public static void clear() {
        map.clear();
    }

    public static class Entry {
        private final String value;
        private long expirationTime;

        Entry(String value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        public void setExpirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
        }

        public String getValue() {
            return value;
        }
    }
}
