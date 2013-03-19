package cz.muni.fi.logger;

public class LoggerFactory {
    
    public static <T extends AbstractNamespace> T getLogger(Class<T> c) {
        try {
            T logger = c.newInstance();
            logger.setLoggerName(c.getCanonicalName());
            return logger;
        } catch (InstantiationException | IllegalAccessException ex) {
            //TODO...
            return null;
        }
    }
}
