package LOGGER;


public class L_EntityB extends Logger {

    public static void method1(String param1, boolean param2) {
        log(new String[]{"param1","param2"}, param1, param2);
    }

    public static void method2(int parameter) {
        log(new String[]{"parameter"}, parameter);
    }
}
