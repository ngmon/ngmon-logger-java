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
import org.apache.logging.log4j.LogManager;

public abstract class Logger {
    
    private static org.apache.logging.log4j.Logger LOG = LogManager.getLogger("");
    
    private static ObjectMapper mapper = new ObjectMapper();
    private static Map<String, Map<String, String>> schemas = new HashMap<>();
    
    private String eventJson = "";
    private String entity = "";
    private String schemaPack = "";
    private String eventType = "";
    private String[] paramNames = new String[]{};
    
    public void fatal() {
        LOG.fatal(eventJson);
    }
    
    public void error() {
        LOG.error(eventJson);
    }
    
    public void warn() {
        LOG.warn(eventJson);
    }
    
    public void info() {
        LOG.info(eventJson);
    }
    
    public void debug() {
        LOG.debug(eventJson);
    }
    
    public void trace() {
        LOG.trace(eventJson);
    }

    public void log() {
        debug();
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
    
    protected Logger log(Object... paramValues) {
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
            initLoggers(entitySchema);
            if ((schemas.get(entitySchema) != null) && (schemas.get(entitySchema).get(eventType) != null)) {
                pathToEventTypeSchema = schemas.get(entitySchema).get(eventType);
            }
        }
        
        eventJson = JSONer.getEventJson(entity, eventType, pathToEventTypeSchema, paramNames, paramValues);
        
        return this;
    }

    protected void setNames(String entity, String schemaPack, String eventType, String[] paramNames) {
        this.entity = entity.substring(2); //odstranit prefix L_ z nazvu entity
        
        //odstranit prefix LOGGER z baliku
        if (schemaPack.indexOf('.') == -1) {
            this.schemaPack = "";
        } else {
            this.schemaPack = schemaPack.substring(schemaPack.indexOf('.') + 1);
        }
        this.eventType = eventType;
        this.paramNames = paramNames;
    }
}
