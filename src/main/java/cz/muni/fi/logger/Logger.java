package cz.muni.fi.logger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public class Logger<T extends AbstractNamespace<T>> {
    
    private T namespace;
    
    protected Logger(Class<T> c, T namespace) {
        namespace.setLogger(LogManager.getLogger(c.getCanonicalName()));
        this.namespace = namespace;
    }
    
    public T fatal() {
        namespace.setLevel(Level.FATAL);
        return namespace;
    }
    
    public T error() {
        namespace.setLevel(Level.ERROR);
        return namespace;
    }
    
    public T warn() {
        namespace.setLevel(Level.WARN);
        return namespace;
    }
    
    public T info() {
        namespace.setLevel(Level.INFO);
        return namespace;
    }
    
    public T debug() {
        namespace.setLevel(Level.DEBUG);
        return namespace;
    }
    
    public T trace() {
        namespace.setLevel(Level.TRACE);
        return namespace;
    }
}
