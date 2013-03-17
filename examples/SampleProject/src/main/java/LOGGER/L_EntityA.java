package LOGGER;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.Logger;

public class L_EntityA extends Logger<L_EntityA> {

    private static final String SCHEMA_PACK = "";
    private static final String SCHEMA_NAME = "EntityA";

    public void method1(String param1, boolean param2) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method1", new String[]{"param1","param2"}, param1, param2);
    }

    public void method2(String parameter) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method2", new String[]{"parameter"}, parameter);
    }

    @Namespace("x.y")
    public void method3(String p1, String p2, String p3) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method3", new String[]{"p1","p2","p3"}, p1, p2, p3);
    }
}
