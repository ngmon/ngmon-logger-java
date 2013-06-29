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

    private String level = "";
    
    public AbstractNamespace tag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public void log() {
        this.logger.log(this.namespace, this.eventName, this.tags, this.paramNames, this.paramValues);
        this.level = "";
        this.tags.clear();
    }

    public void debug() {
        this.level = "DEBUG";
        this.log();
    }
    public void error() {
        this.level = "ERROR";
        this.log();
    }
    public void info() {
        this.level = "INFO";
        this.log();
    }
    public void trace(){
        this.level = "TRACE";
        this.log();
    }
    public void warn() {
        this.level = "WARN";
        this.log();
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