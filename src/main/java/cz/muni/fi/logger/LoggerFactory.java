package cz.muni.fi.logger;

public class LoggerFactory {
    
    public static <T extends Logger<T>> T getLogger(Class<T> c) {
        try {        
            //TODO ?
            T logger = c.newInstance();
            logger.setLogger(c.getCanonicalName());
            return logger;
        } catch (InstantiationException | IllegalAccessException ex) {
            //TODO...
            return null;
        }
    }
}
