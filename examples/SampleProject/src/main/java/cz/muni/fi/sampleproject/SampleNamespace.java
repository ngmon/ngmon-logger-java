package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.json.EventTypeDetails;
import cz.muni.fi.json.JSONer;

@Namespace
public class SampleNamespace {
    
    public static EventTypeDetails event1(String param1, int param2) {
        return JSONer.getEventTypeDetails("event1", new String[]{"param1","param2"}, param1, param2);
    }
    
    public static EventTypeDetails event2(double a, float b, boolean c) {
        return JSONer.getEventTypeDetails("event2", new String[]{"a","b","c"}, a, b, c);
    }
}
