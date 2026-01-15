package sp.phone.http.cookie;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CookieHeaderUtil {

    private CookieHeaderUtil() {
    }

    public static String mergeCookieHeaders(@Nullable String... cookieHeaders) {
        Map<String, String> map = new LinkedHashMap<>();
        if (cookieHeaders == null) {
            return "";
        }
        for (String header : cookieHeaders) {
            parseInto(map, header);
        }
        return toHeader(map);
    }

    public static Map<String, String> parseCookieHeader(@Nullable String cookieHeader) {
        Map<String, String> map = new LinkedHashMap<>();
        parseInto(map, cookieHeader);
        return map;
    }

    private static void parseInto(Map<String, String> map, @Nullable String cookieHeader) {
        if (cookieHeader == null) {
            return;
        }
        String trimmed = cookieHeader.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split(";");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if (!name.isEmpty()) {
                map.put(name, value);
            }
        }
    }

    private static String toHeader(Map<String, String> map) {
        if (map.isEmpty()) {
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

