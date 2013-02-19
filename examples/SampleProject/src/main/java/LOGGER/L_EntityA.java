package LOGGER;

import cz.muni.fi.annotation.Namespace;

public class L_EntityA extends Logger {

    public static void method1(String param1, boolean param2) {
        log(new String[]{"param1","param2"}, param1, param2);
    }

    public static void method2(String parameter) {
        log(new String[]{"parameter"}, parameter);
    }

    @Namespace("x.y")
    public static void method3(String p1, String p2, String p3) {
        log(new String[]{"p1","p2","p3"}, p1, p2, p3);
    }
}
