package cz.muni.fi.logger;

public class LoggerFactory {
    
    public static <T extends AbstractNamespace<T>> Logger<T> getLogger(Class<T> c) {
        try {
            Logger<T> logger = new Logger<>(c, c.newInstance());
            return logger;
        } catch (InstantiationException | IllegalAccessException ex) {
            //TODO...
            return null;
        }
    }
}
