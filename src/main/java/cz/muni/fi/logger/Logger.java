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
        return namespaceWithLevel(Level.FATAL);
    }
    
    public T error() {
        return namespaceWithLevel(Level.ERROR);
    }
    
    public T warn() {
        return namespaceWithLevel(Level.WARN);
    }
    
    public T info() {
        return namespaceWithLevel(Level.INFO);
    }
    
    public T debug() {
        return namespaceWithLevel(Level.DEBUG);
    }
    
    public T trace() {
        return namespaceWithLevel(Level.TRACE);
    }
    
    private T namespaceWithLevel(Level level) {
        namespace.setLevel(level);
        return namespace;
    }
}
