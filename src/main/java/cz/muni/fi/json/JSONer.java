package cz.muni.fi.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;

public class JSONer {
    
    public static String getEventJson(String entity, String eventType, String eventTypeSchema, String[] names, Object... values) {
        JsonFactory jsonFactory = new JsonFactory();
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();
            
            json.writeStringField("entity", entity);
            json.writeStringField("eventType", eventType);
            
            json.writeObjectFieldStart(eventTypeSchema + "#");
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
