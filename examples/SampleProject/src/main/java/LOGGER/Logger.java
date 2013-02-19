package LOGGER;

import java.util.HashMap;
import java.util.Map;

public class Logger {

    public static void log(String[] names, Object... values) {
        Map<String, Object> map = new HashMap<>();

        for (int i = 0; i < names.length; i++) {
            map.put(names[i], values[i]);
        }
    }
}
