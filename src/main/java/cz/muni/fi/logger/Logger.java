package cz.muni.fi.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import cz.muni.fi.json.JSONer;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public abstract class Logger<T extends Logger<T>> {
    
    private static org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    private Level level = Level.DEBUG;
    
    private static ObjectMapper mapper = new ObjectMapper();
    private static Map<String, Map<String, String>> schemas = new HashMap<>();
    
    @SuppressWarnings("unchecked")
    public T fatal() {
        level = Level.FATAL;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T error() {
        level = Level.ERROR;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T warn() {
        level = Level.WARN;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T info() {
        level = Level.INFO;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T debug() {
        level = Level.DEBUG;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T trace() {
        level = Level.TRACE;
        return (T) this;
    }
    
    public static void initAll() {
        schemas = new HashMap<>();
        final String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "ENTITIES";
        try {
            Files.walkFileTree(FileSystems.getDefault().getPath(p), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    Map<String,String> methods = new HashMap<>();
                    JsonNode root = mapper.readTree(path.toFile());
                    ArrayNode eventTypes = (ArrayNode)(root.get("eventTypes"));
                    for (int i = 0; i < eventTypes.size(); i++) {
                        JsonNode methodNode = eventTypes.get(i);
                        Iterator<String> it = methodNode.fieldNames();
                        String methodName = it.next();
                        methods.put(methodName, methodNode.get(methodName).textValue() + ".json");
                    }
                    String entity = path.toString().substring(p.length() + 1).replace(File.separatorChar, '.');
                    schemas.put(entity.substring(0, entity.length() - 5), methods);
                    
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }
    
    public static void initLoggers(String... entities) {
        schemas = new HashMap<>();
        String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "ENTITIES";
        
        for (String entity : entities) {
            Map<String,String> methods = new HashMap<>();
            try {
                JsonNode root = mapper.readTree(new File(p + File.separator + entity.replace('.', File.separatorChar) + ".json"));
                ArrayNode eventTypes = (ArrayNode)(root.get("eventTypes"));
                for (int i = 0; i < eventTypes.size(); i++) {
                    JsonNode methodNode = eventTypes.get(i);
                    Iterator<String> it = methodNode.fieldNames();
                    String methodName = it.next();
                    methods.put(methodName, methodNode.get(methodName).textValue() + ".json");
                }
                schemas.put(entity, methods);
            } catch (IOException ex) {
            }
        }
    }
    
    public void log(String schemaPack, String entity, String eventType, String[] paramNames, Object... paramValues) {
        String pathToEventTypeSchema = "";
        
        String entitySchema;
        if (schemaPack.length() == 0) {
            entitySchema = entity;
        } else {
            entitySchema = schemaPack + "." + entity;
        }
        
        if ((schemas.get(entitySchema) != null) && (schemas.get(entitySchema).get(eventType) != null)) {
            pathToEventTypeSchema = schemas.get(entitySchema).get(eventType);
        } else {
            try {
                String path = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "ENTITIES" 
                        + File.separator + schemaPack.replace('.', File.separatorChar) + File.separator + entity + ".json";
                JsonNode root = mapper.readTree(new File(path));

                ArrayNode eventTypes = (ArrayNode)(root.get("eventTypes"));

                for (int i = 0; i < eventTypes.size(); i++) {
                    JsonNode methodNode = eventTypes.get(i);
                    Iterator<String> it = methodNode.fieldNames();
                    String methodName = it.next();
                    if (methodName.equals(eventType)) {
                        pathToEventTypeSchema = methodNode.get(methodName).textValue() + ".json";
                        break;
                    }
                }
            } catch (IOException e) {
            }
        }
        
        String eventJson = JSONer.getEventJson(entity, eventType, pathToEventTypeSchema, paramNames, paramValues);
        
        switch (level) {
            case FATAL: LOG.fatal(eventJson); break;
            case ERROR: LOG.error(eventJson); break;
            case WARN:  LOG.warn(eventJson);  break;
            case INFO:  LOG.info(eventJson);  break;
            case DEBUG: LOG.debug(eventJson); break;
            case TRACE: LOG.trace(eventJson); break;
        }
        
        level = Level.DEBUG;
    }
    
}
