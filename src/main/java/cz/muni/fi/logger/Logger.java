package cz.muni.fi.logger;

import cz.muni.fi.json.EventTypeDetails;
import cz.muni.fi.json.JSONer;
import java.util.ArrayList;
import java.util.List;

public abstract class Logger<T extends Logger<T>> {
    
    private List<String> entities = new ArrayList<>();
    
    @SuppressWarnings("unchecked")
    public T tag(String entity) {
        entities.add(entity);
        return (T) this;
    }
    
    public EventTypeDetails log(String eventType, String[] paramNames, Object... paramValues) { //TODO void a loguj.
        List<String> e = new ArrayList<>();
        e.addAll(entities);
        EventTypeDetails etd = new EventTypeDetails();
        etd.setEventType(eventType);
        etd.setEntities(e);
        etd.setJson(JSONer.getEventTypeJson(paramNames, paramValues));
        entities.clear();
        return etd;
    }
}
