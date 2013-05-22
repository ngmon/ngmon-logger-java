package cz.muni.fi.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class JSONer {
    
    private static JsonFactory jsonFactory = new JsonFactory();
    
    /**
     * Returns a JSON representation of a particular instance of an event type.
     * 
     * @param fqnNS fully qualified name of the @Namespace-annotated class the eventType belongs to
     * @param eventType event type (logging method name)
     * @param tags a list of tags (e.g. entities) associated with this instance of eventType
     * @param names names of eventType's attributes
     * @param values values of eventType's attributes
     * @return JSON object containing all given values
     */
    public static String getEventJson(String fqnNS, String eventType, List<String> tags, String[] names, Object... values) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();
            
            json.writeArrayFieldStart("tags");
            for (String t : tags) {
                json.writeString(t);
            }
            json.writeEndArray();
            
            json.writeStringField("schema", fqnNS + ".json#/definitions/" + eventType);
            
            json.writeObjectFieldStart("properties");
            for (int i = 0; i < names.length; i++) {
                json.writeFieldName(names[i]);
                if (values[i] instanceof Number) {
                    if (((Number)values[i]).doubleValue() % 1 == 0) {
                        json.writeNumber(((Number)values[i]).longValue());
                    } else {
                        json.writeNumber(((Number)values[i]).doubleValue());
                    }
                } else {
                    if (values[i] instanceof Boolean) {
                        json.writeBoolean((boolean)values[i]);
                    } else {
                        json.writeString(values[i].toString());
                    }
                }
            }
            json.writeEndObject();
            
            json.writeEndObject();
        } catch (IOException e) {
        }
        
        return writer.toString();
    }
}