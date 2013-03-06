package cz.muni.fi.json;

import java.util.List;

public class EventTypeDetails {
    
    private String eventType;
    private List<String> entities;
    private String json;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }
    
    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }
    
    @Override
    public String toString() {
        return "'" + this.eventType + "': " + this.entities.toString() + "\n" + this.json;
    }
}
