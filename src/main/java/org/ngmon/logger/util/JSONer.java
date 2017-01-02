package org.ngmon.logger.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.ngmon.logger.level.Level;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

public class JSONer {

    private static JsonFactory jsonFactory = new JsonFactory();

    /**
     * Returns a JSON representation of a particular instance of an event type.
     *
     * @param fqnNS     fully qualified name of the @Namespace-annotated class the eventType belongs to
     * @param eventType event type (logging method name)
     * @param tags      a list of tags (e.g. entities) associated with this instance of eventType
     * @param names     names of eventType's attributes
     * @param values    values of eventType's attributes
     * @return JSON object containing all given values
     */
    public static String getEventJson(String fqnNS, String eventType, List<String> tags, String[] names, Object[] values, Level level) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();

            json.writeObjectFieldStart("Event");

            json.writeArrayFieldStart("tags");
            for (String t : tags) {
                json.writeString(t);
            }
            json.writeEndArray();

            json.writeStringField("type", eventType);
            json.writeStringField("level", level.toString());

            json.writeObjectFieldStart("_");
            json.writeStringField("schema", fqnNS);
            for (int i = 0; i < names.length; i++) {
                json.writeObjectField(names[i], values[i]);
            }
            json.writeEndObject();

            json.writeEndObject();

            json.writeEndObject();
        } catch (IOException e) {
        }

        return writer.toString();
    }

    public static String getEventJson2(String fqnNS, String eventType, List<String> tags, String[] names, Object[] values, Level level) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();

            json.writeArrayFieldStart("tags");
            for (String t : tags) {
                json.writeString(t);
            }
            json.writeEndArray();

            json.writeStringField("level", level.toString());
            json.writeStringField("event_namespace", fqnNS);
            json.writeStringField("event_type", eventType);
            json.writeStringField("_", getPayload(names, values));

            json.writeEndObject();
        } catch (IOException e) {
        }

        return writer.toString();
    }

    static String getPayload(String[] names, Object[] values) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();
            for (int i = 0; i < names.length; i++) {
                json.writeObjectField(names[i], values[i]);
            }
            json.writeEndObject();
        } catch (IOException e) {
        }

        return writer.toString();
    }

    public static String getEventJsonSimple(String type, Map<String, Object> data) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = jsonFactory.createGenerator(writer)) {
            json.writeStartObject();

            json.writeObjectFieldStart("Event");
            json.writeStringField("type", type);

            json.writeObjectFieldStart("_");
            for (Map.Entry<String, Object> objectEntry : data.entrySet()) {

                if (objectEntry.getKey().equals("tags")) {
                    String[] tags = (String[]) objectEntry.getValue();
                    json.writeArrayFieldStart("tags");
                    for (String t : tags) {
                        json.writeString(t);
                    }
                    json.writeEndArray();

                    continue;
                }
                json.writeObjectField(objectEntry.getKey(), objectEntry.getValue());
            }

            json.writeEndObject();

            json.writeEndObject();

            json.writeEndObject();
        } catch (IOException e) {
        }

        return writer.toString();
    }
}