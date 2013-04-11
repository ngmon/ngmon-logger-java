package cz.muni.fi.logger;

public class LoggerFactory {
    
    public static <T extends AbstractNamespace> T getLogger(Class<T> c, Logger externalLogger) {
        try {
            T namespaceLogger = c.newInstance();
            namespaceLogger.setFqnNS(c.getCanonicalName());
            namespaceLogger.setLogger(externalLogger);
            return namespaceLogger;
        } catch (InstantiationException | IllegalAccessException ex) {
            return null;
        }
    }
}
