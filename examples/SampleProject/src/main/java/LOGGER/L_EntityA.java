package LOGGER;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.Logger;

public class L_EntityA extends Logger {

    public Logger method1(String param1, boolean param2) {
        return log(param1,param2);
    }

    public Logger method2(String parameter) {
        return log(parameter);
    }

    @Namespace("x.y")
    public Logger method3(String p1, String p2, String p3) {
        return log(p1, p2, p3);
    }
}
