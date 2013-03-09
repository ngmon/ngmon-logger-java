package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;

public abstract class Logger<T extends Logger<T>> {
    
    private List<String> entities = new ArrayList<>();
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    public void setLogger(String name) {
        LOG = LogManager.getLogger(name);
    }
    
    @SuppressWarnings("unchecked")
    public T tag(String entity) {
        entities.add(entity);
        return (T) this;
    }
    
    public void log(String fqnNS, String eventType, String[] paramNames, Object... paramValues) {
        List<String> e = new ArrayList<>();
        e.addAll(entities);
        entities.clear();
        
        String eventJson = JSONer.getEventJson(fqnNS, eventType, e, paramNames, paramValues);
        
        LOG.debug(eventJson); //TODO Level?
    }
}
