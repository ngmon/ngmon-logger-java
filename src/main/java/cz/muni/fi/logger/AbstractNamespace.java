package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public abstract class AbstractNamespace<T extends AbstractNamespace<T>> {
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    private List<String> entities = new ArrayList<>();
    private Level level = Level.DEBUG;
    
    public void setLevel(Level level) {
        this.level = level;
    }
    
    //TODO doc: nech to nevolaju zvonka, lebo sa tym vysoko pravdepodobne pokazi meno loggera a tym padom bude zly odkaz na schemu
    public void setLogger(org.apache.logging.log4j.Logger logger) {
        this.LOG = logger;
    }
    
    @SuppressWarnings("unchecked")
    public T tag(String entity) {
        entities.add(entity);
        return (T) this;
    }
    
    public void log(String eventType, String[] paramNames, Object... paramValues) {
        List<String> e = new ArrayList<>();
        e.addAll(entities);
        entities.clear();
        
        String eventJson = JSONer.getEventJson(LOG.getName(), eventType, e, paramNames, paramValues);
        
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
