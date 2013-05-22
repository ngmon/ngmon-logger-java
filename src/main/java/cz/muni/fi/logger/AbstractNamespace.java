package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNamespace {
    
    private Logger LOGGER;
    
    private String fqnNS = "";
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
    
    public void log() {
        String eventJson = JSONer.getEventJson(fqnNS, methodName, tags, paramNames, paramValues);
        tags.clear();
        
        LOGGER.log(eventJson);
    }
    
    protected void setFqnNS(String fqnNS) {
        this.fqnNS = fqnNS.replace('.', '/');
    }
    
    protected void setLogger(Logger logger) {
        this.LOGGER = logger;
    }
    
    protected void setNames(String methodName, String[] paramNames) {
        this.methodName = methodName;
        this.paramNames = paramNames;
    }
}