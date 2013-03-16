package LOGGER;

import cz.muni.fi.logger.Logger;

public class L_EntityB extends Logger {

    private static final String SCHEMA_PACK = "";
    private static final String SCHEMA_NAME = "EntityB";

    public static void method1(String param1, boolean param2) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method1", new String[]{"param1","param2"}, param1, param2);
    }

    public static void method2(String parameter) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method2", new String[]{"parameter"}, parameter);
    }
}
