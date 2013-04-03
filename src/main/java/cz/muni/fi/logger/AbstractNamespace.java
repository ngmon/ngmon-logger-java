package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public abstract class AbstractNamespace {
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    private String methodName = "";
    private List<String> tags = new ArrayList<>();
    private String[] paramNames = new String[]{};
    private Object[] paramValues = new Object[]{};
    
    public AbstractNamespace tag(String tag) {
        tags.add(tag);
        return this;
    }
    
    protected AbstractNamespace log(Object... paramValues) {
        this.paramValues = paramValues;
        return this;
    }
    
    public void fatal() {
        logWithLevel(Level.FATAL);
    }
    
    public void error() {
        logWithLevel(Level.ERROR);
    }
    
    public void warn() {
        logWithLevel(Level.WARN);
    }
    
    public void info() {
        logWithLevel(Level.INFO);
    }
    
    public void debug() {
        logWithLevel(Level.DEBUG);
    }
    
    public void trace() {
        logWithLevel(Level.TRACE);
    }
    
    public void log() {
        logWithLevel(Level.DEBUG);
    }
    
    private void logWithLevel(Level level) {
        String eventJson = JSONer.getEventJson(LOG.getName(), methodName, tags, paramNames, paramValues);
        tags.clear();
        
        switch (level) {
            case FATAL: LOG.fatal(eventJson); break;
            case ERROR: LOG.error(eventJson); break;
            case WARN:  LOG.warn(eventJson);  break;
            case INFO:  LOG.info(eventJson);  break;
            case DEBUG: LOG.debug(eventJson); break;
            case TRACE: LOG.trace(eventJson); break;
            default: LOG.debug(eventJson); break;
        }
    }
    
    protected void setLoggerName(String name) {
        LOG = LogManager.getLogger(name);
    }
    
    protected void setNames(String methodName, String[] paramNames) {
        this.methodName = methodName;
        this.paramNames = paramNames;
    }
}