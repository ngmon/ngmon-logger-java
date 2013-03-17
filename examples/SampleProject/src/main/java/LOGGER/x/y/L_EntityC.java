package LOGGER.x.y;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.Logger;

public class L_EntityC extends Logger<L_EntityC> {

    private static final String SCHEMA_PACK = "x.y";
    private static final String SCHEMA_NAME = "EntityC";

    @Namespace("")
    public void method1(String param1, boolean param2) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method1", new String[]{"param1","param2"}, param1, param2);
    }

    public void method3(String p1, String p2, String p3) {
        log(SCHEMA_PACK, SCHEMA_NAME, "method3", new String[]{"p1","p2","p3"}, p1, p2, p3);
    }
}
