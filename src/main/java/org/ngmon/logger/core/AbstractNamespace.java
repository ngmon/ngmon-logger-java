package org.ngmon.logger.core;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNamespace {
    
    private Logger logger;
    
    private String namespace = "";
    private List<String> tags = new ArrayList<>();
	private String methodName = "";
    private String[] paramNames = new String[]{};
    private Object[] paramValues = new Object[]{};
    
    public AbstractNamespace tag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public void log() {
        this.logger.log(this.namespace, this.methodName, this.tags, this.paramNames, this.paramValues);
        this.tags.clear();
    }
    
    protected void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    protected void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    protected void inject(String methodName, String[] paramNames, Object[] paramValues) {
        this.methodName = methodName;
        this.paramNames = paramNames;
	    this.paramValues = paramValues;
    }
}