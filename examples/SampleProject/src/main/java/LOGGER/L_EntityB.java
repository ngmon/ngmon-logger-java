package LOGGER;

import cz.muni.fi.logger.Logger;

public class L_EntityB extends Logger {

    public Logger method1(String param1, boolean param2) {
        return log(param1, param2);
    }

    public Logger method2(String parameter) {
        return log(parameter);
    }
}
