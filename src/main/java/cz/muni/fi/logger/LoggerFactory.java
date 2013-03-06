package cz.muni.fi.logger;

public class LoggerFactory {
    
    public static <T extends Logger<T>> T getLogger(Class<T> c) {
        try {        
            //TODO ?
            return c.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            //TODO...
            return null;
        }
    }
}
