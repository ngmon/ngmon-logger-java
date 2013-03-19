package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;

public abstract class AbstractNamespace {
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    private List<String> entities = new ArrayList<>();
    private String eventJson = "";
    
    public AbstractNamespace tag(String entity) {
        entities.add(entity);
        return this;
    }
    
    protected AbstractNamespace log(String eventType, String[] paramNames, Object... paramValues) {
        List<String> e = new ArrayList<>();
        e.addAll(entities);
        entities.clear();
        
        this.eventJson = JSONer.getEventJson(LOG.getName(), eventType, e, paramNames, paramValues);
        
        return this;
    }
    
    public void fatal() {
        LOG.fatal(eventJson);
    }
    
    public void error() {
        LOG.error(eventJson);
    }
    
    public void warn() {
        LOG.warn(eventJson);
    }
    
    public void info() {
        LOG.info(eventJson);
    }
    
    public void debug() {
        LOG.debug(eventJson);
    }
    
    public void trace() {
        LOG.trace(eventJson);
    }
    
    public void log() { //TODO default level?
        debug();
    }
    
    protected void setLoggerName(String name) {
        LOG = LogManager.getLogger(name);
    }
}