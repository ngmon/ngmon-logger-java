package org.ngmon.logger.core;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNamespace {
    
    private Logger logger;
    
    private String namespace = "";
    private List<String> tags = new ArrayList<>();
	private String eventName = "";
    private String[] paramNames = new String[]{};
    private Object[] paramValues = new Object[]{};

    public AbstractNamespace tag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public void log(int level) {
        this.logger.log(this.namespace, this.eventName, this.tags, this.paramNames, this.paramValues, level);
        this.tags.clear();
    }

    public void debug() {
        this.log(10000);
    }
    public void error() {
        this.log(40000);
    }
    public void info() {
        this.log(20000);
    }
    public void trace(){
        this.log(5000);
    }
    public void warn() {
        this.log(30000);
    }

    protected void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    protected void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    protected void inject(String eventName, String[] paramNames, Object[] paramValues) {
        this.eventName = eventName;
        this.paramNames = paramNames;
	    this.paramValues = paramValues;
    }
}