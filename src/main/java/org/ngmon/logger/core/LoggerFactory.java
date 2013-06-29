package org.ngmon.logger.core;

public class LoggerFactory {
    
    public static <T extends AbstractNamespace> T getLogger(Class<T> namespaceClass, Logger externalLogger) {
        try {
            T namespaceLogger = namespaceClass.newInstance();
            namespaceLogger.setNamespace(namespaceClass.getCanonicalName());
            namespaceLogger.setLogger(externalLogger);

            return namespaceLogger;
        } catch (InstantiationException | IllegalAccessException ex) {
            ex.printStackTrace();
	        return null;
        }
    }
}
