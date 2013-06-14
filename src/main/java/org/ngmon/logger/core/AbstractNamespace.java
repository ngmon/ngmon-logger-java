package org.ngmon.logger.core;

import org.ngmon.logger.json.JSONer;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNamespace {
    
    private Logger LOGGER;
    
    private String fqnNS = "";

    private List<String> tags = new ArrayList<>();
	private String methodName = "";
    private String[] paramNames = new String[]{};
    private Object[] paramValues = new Object[]{};
    
    public AbstractNamespace tag(String tag) {
        tags.add(tag);
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
    
    protected void inject(String methodName, String[] paramNames, Object[] paramValues) {
        this.methodName = methodName;
        this.paramNames = paramNames;
	    this.paramValues = paramValues;
    }
}