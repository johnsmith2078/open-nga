package sp.phone.http.cookie;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.base.util.PreferenceUtils;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public final class NgaCookieStore {
    private static final String PREF_KEY = "pref_nga_http_cookie_store_v1";

    private static final class StoredCookie {
        public String name;
        public String value;
        public long expiresAt;
    }

    private static final class SingletonHolder {
        private static final NgaCookieStore INSTANCE = new NgaCookieStore();
    }

    public static NgaCookieStore getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private NgaCookieStore() {
    }

    public synchronized void saveFromResponse(HttpUrl url, List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }
        Map<String, Map<String, StoredCookie>> store = loadStore();
        String host = url.host();
        Map<String, StoredCookie> hostCookies = store.get(host);
        if (hostCookies == null) {
            hostCookies = new LinkedHashMap<>();
            store.put(host, hostCookies);
        }
        long now = System.currentTimeMillis();
        for (String header : setCookieHeaders) {
            if (TextUtils.isEmpty(header)) {
                continue;
            }
            Cookie cookie = Cookie.parse(url, header);
            if (cookie == null) {
                continue;
            }
            if (cookie.expiresAt() <= now) {
                hostCookies.remove(cookie.name());
                continue;
            }
            StoredCookie sc = new StoredCookie();
            sc.name = cookie.name();
            sc.value = cookie.value();
            sc.expiresAt = cookie.expiresAt();
            hostCookies.put(sc.name, sc);
        }
        pruneExpired(store, now);
        persistStore(store);
    }

    public synchronized String getCookieHeader(HttpUrl url) {
        long now = System.currentTimeMillis();
        Map<String, Map<String, StoredCookie>> store = loadStore();
        Map<String, StoredCookie> hostCookies = store.get(url.host());
        if (hostCookies == null || hostCookies.isEmpty()) {
            return "";
        }
        boolean changed = false;
        Map<String, String> headerMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, StoredCookie>> it = hostCookies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StoredCookie> e = it.next();
            StoredCookie sc = e.getValue();
            if (sc == null || sc.expiresAt <= now || TextUtils.isEmpty(sc.name)) {
                it.remove();
                changed = true;
                continue;
            }
            headerMap.put(sc.name, sc.value);
        }
        if (changed) {
            pruneExpired(store, now);
            persistStore(store);
        }
        return toHeader(headerMap);
    }

    @Nullable
    public synchronized Map<String, String> getCookieMap(HttpUrl url) {
        long now = System.currentTimeMillis();
        Map<String, Map<String, StoredCookie>> store = loadStore();
        Map<String, StoredCookie> hostCookies = store.get(url.host());
        if (hostCookies == null || hostCookies.isEmpty()) {
            return null;
        }
        boolean changed = false;
        Map<String, String> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, StoredCookie>> it = hostCookies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StoredCookie> e = it.next();
            StoredCookie sc = e.getValue();
            if (sc == null || sc.expiresAt <= now || TextUtils.isEmpty(sc.name)) {
                it.remove();
                changed = true;
                continue;
            }
            map.put(sc.name, sc.value);
        }
        if (changed) {
            pruneExpired(store, now);
            persistStore(store);
        }
        return map;
    }

    public synchronized void clear() {
        PreferenceUtils.edit().remove(PREF_KEY).apply();
    }

    private static void pruneExpired(Map<String, Map<String, StoredCookie>> store, long now) {
        if (store == null || store.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, Map<String, StoredCookie>>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Map<String, StoredCookie>> hostEntry = it.next();
            Map<String, StoredCookie> hostCookies = hostEntry.getValue();
            if (hostCookies == null || hostCookies.isEmpty()) {
                it.remove();
                continue;
            }
            Iterator<Map.Entry<String, StoredCookie>> it2 = hostCookies.entrySet().iterator();
            while (it2.hasNext()) {
                Map.Entry<String, StoredCookie> e = it2.next();
                StoredCookie sc = e.getValue();
                if (sc == null || sc.expiresAt <= now) {
                    it2.remove();
                }
            }
            if (hostCookies.isEmpty()) {
                it.remove();
            }
        }
    }

    private Map<String, Map<String, StoredCookie>> loadStore() {
        String json = PreferenceUtils.getData(PREF_KEY, "");
        if (TextUtils.isEmpty(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, Map<String, StoredCookie>> data = JSON.parseObject(
                    json,
                    new TypeReference<Map<String, Map<String, StoredCookie>>>() {
                    });
            return data != null ? data : new HashMap<>();
        } catch (Throwable ignored) {
            return new HashMap<>();
        }
    }

    private void persistStore(Map<String, Map<String, StoredCookie>> store) {
        if (store == null) {
            PreferenceUtils.edit().remove(PREF_KEY).apply();
            return;
        }
        PreferenceUtils.putData(PREF_KEY, JSON.toJSONString(store));
    }

    private static String toHeader(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
