package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import org.apache.logging.log4j.LogManager;

public abstract class AbstractNamespace {
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    private String eventJson = "";
    private String methodName = "";
    private String[] paramNames = new String[]{};
    
    public AbstractNamespace tag(String tag) {
        eventJson = JSONer.addTagToEventJson(tag, eventJson);
        return this;
    }
    
    protected AbstractNamespace log(Object... paramValues) {
        eventJson = JSONer.getEventJson(LOG.getName(), methodName, paramNames, paramValues);
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
    
    protected void setNames(String methodName, String[] paramNames) {
        this.methodName = methodName;
        this.paramNames = paramNames;
    }
}