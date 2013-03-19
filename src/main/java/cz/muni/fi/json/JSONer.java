package cz.muni.fi.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class JSONer {
    
    public static String getEventJson(String fqnNS, String eventType, List<String> tags, String[] names, Object... values) {
        JsonFactory jsonFactory = new JsonFactory();
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();
            
            json.writeArrayFieldStart("tags");
            for (String e : tags) {
                json.writeString(e);
            }
            json.writeEndArray();
            
            json.writeObjectFieldStart(fqnNS + ".json#/definitions/" + eventType);
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
