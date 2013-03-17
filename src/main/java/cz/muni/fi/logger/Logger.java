package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public abstract class Logger<T extends Logger<T>> {
    
    private List<String> entities = new ArrayList<>();
    private Level level = Level.DEBUG;
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    public void setLogger(String name) {
        LOG = LogManager.getLogger(name);
    }
    
    @SuppressWarnings("unchecked")
    public T tag(String entity) {
        entities.add(entity);
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T fatal() {
        level = Level.FATAL;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T error() {
        level = Level.ERROR;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T warn() {
        level = Level.WARN;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T info() {
        level = Level.INFO;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T debug() {
        level = Level.DEBUG;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T trace() {
        level = Level.TRACE;
        return (T) this;
    }
    
    public void log(String fqnNS, String eventType, String[] paramNames, Object... paramValues) {
        List<String> e = new ArrayList<>();
        e.addAll(entities);
        entities.clear();
        
        String eventJson = JSONer.getEventJson(fqnNS, eventType, e, paramNames, paramValues);
        
        switch (level) {
            case FATAL: LOG.fatal(eventJson); break;
            case ERROR: LOG.error(eventJson); break;
            case WARN:  LOG.warn(eventJson);  break;
            case INFO:  LOG.info(eventJson);  break;
            case DEBUG: LOG.debug(eventJson); break;
            case TRACE: LOG.trace(eventJson); break;
        }
        
        level = Level.DEBUG;
    }
}
