package cz.muni.fi.logger;

import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNamespace<T extends AbstractNamespace<T>> {
    
    private Logger LOGGER;
    
    private String fqnNS = "";
    private String methodName = "";
    private List<String> tags = new ArrayList<>();
    private String[] paramNames = new String[]{};
    
    @SuppressWarnings("unchecked")
    public T tag(String tag) {
        tags.add(tag);
        return (T) this;
    }
    
    protected void log(Object... paramValues) {
        String eventJson = JSONer.getEventJson(fqnNS, methodName, tags, paramNames, paramValues);
        tags.clear();
        
        LOGGER.log(eventJson);
    }
    
    protected void setFqnNS(String fqnNS) {
        this.fqnNS = fqnNS;
    }
    
    protected void setLogger(Logger logger) {
        this.LOGGER = logger;
    }
    
    protected void setNames(String methodName, String[] paramNames) {
        this.methodName = methodName;
        this.paramNames = paramNames;
    }
}