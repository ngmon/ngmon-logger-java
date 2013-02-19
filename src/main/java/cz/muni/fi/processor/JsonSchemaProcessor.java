package cz.muni.fi.processor;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.muni.fi.annotation.Namespace;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO odstranit spätné referencie na entity zo schem
    //TODO ratam natvrdo so standardnou mavenovskou strukturou (src/main/java apod.) ...to asi nie je uplne dobre?
    
    private Filer filer;
    private Messager messager;
    
    private static final String ENTITIES_BASE_PKG = "ENTITIES";
    private static final String EVENTTYPES_BASE_PKG = "SCHEMAS";
    private static final String CLASSES_BASE_PKG = "LOGGER";
    private static final String LOGGER = "Logger";
    private static final String CLASS_PREFIX = "L_";
    
    private long lastBuildTime = 0;
    private boolean firstRound = true;
    private List<Element> schemasToGenerate = new ArrayList<>();
    private List<String> newClasses = new ArrayList<>();

    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
        
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("src/main/resources/config.properties"));
            lastBuildTime = Long.parseLong(properties.getProperty("lastBuildTime"));
    	} catch (IOException ex) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Error reading file src/main/resources/config.properties");
        }
    }

    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        List<String> processedClasses = new ArrayList<>();
        
        if (! env.processingOver()) {
            if (firstRound) {
                for (Element element : env.getRootElements()) {
                    String pack = element.getEnclosingElement().toString();
                    
                    if (pack.equals(CLASSES_BASE_PKG) || pack.startsWith(CLASSES_BASE_PKG + ".")) {
                        processedClasses.add(pack + "." + element.getSimpleName().toString());
                        
                        //process only classes changed since the last build
                        String path = "src" + File.separatorChar + "main" + File.separatorChar + "java" + File.separatorChar
                            + pack.replace('.', File.separatorChar) + File.separatorChar + element.getSimpleName() + ".java";
                        Path p = FileSystems.getDefault().getPath(path);
                        long lastModified;
                        try {
                            lastModified = Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException ex) {
                            lastModified = new Date().getTime();
                        }

                        if (lastModified > lastBuildTime) {
                            schemasToGenerate.add(element);
                        }
                    }
                }
            }
        } else { //last round - generate schemas for all classes (except for those that have just been created)
            for (Element element : schemasToGenerate) {
                String pack = element.getEnclosingElement().toString();
                String elementName = element.getSimpleName().toString();
                
                if (newClasses.contains(pack + "." + elementName) || elementName.equals(LOGGER)) {
                    continue;
                }
                
                String entitySchemaName = elementName.substring(CLASS_PREFIX.length());
                try {
                    String classPackage = pack.substring(CLASSES_BASE_PKG.length());
                    String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + 
                            ENTITIES_BASE_PKG + classPackage.replace('.', File.separatorChar) + 
                            File.separatorChar + entitySchemaName + ".json";
                    Path file = FileSystems.getDefault().getPath(path);
                    
                    //create non-existing directories
                    Files.createDirectories(file.getParent());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    
                    //save the original content of this entity schema for later tests
                    JsonNode formerEntitySchema = null;
                    if (file.toFile().exists()) {
                        formerEntitySchema = mapper.readTree(file.toFile());
                    }
                    
                    JsonFactory jsonFactory = new JsonFactory();
                    try (JsonGenerator entitySchema = jsonFactory.createGenerator(file.toFile(), JsonEncoding.UTF8)) {
                        entitySchema.useDefaultPrettyPrinter(); //TODO napisat vlastny, krajsi PrettyPrinter? ten rucne vypisovany JSON vyzeral lepsie ako toto
                        
                        entitySchema.writeStartObject();
                        entitySchema.writeStringField("$schema", "http://json-schema.org/schema#");
                        entitySchema.writeStringField("title", entitySchemaName);
                        entitySchema.writeArrayFieldStart("eventTypes");
                        
                        Set<String> methodNames = new HashSet<>();
                        for (Element e : element.getEnclosedElements()) {
                            if ((e.getKind() == ElementKind.METHOD) && e.getModifiers().contains(Modifier.PUBLIC)) {
                                ExecutableElement method = (ExecutableElement) e;
                                
                                String methodName = method.getSimpleName().toString();
                                String methodSchemaName = method.getSimpleName().toString();
                                //forbid method overloading
                                if (methodNames.contains(methodName)) {
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Method overloading not allowed here", e);
                                } else {
                                    methodNames.add(methodName);
                                }
                                
                                //get parameter names and types
                                List<String> paramNames = new ArrayList<>();
                                List<String> paramTypes = new ArrayList<>();
                                for (VariableElement param : method.getParameters()) {
                                    String typeFull = param.asType().toString();
                                    String type = typeFull.substring(typeFull.lastIndexOf(".") + 1);
                                    switch (type) {
                                        case "String":
                                            paramTypes.add("string");
                                            break;
                                        case "int":
                                            paramTypes.add("integer");
                                            break;
                                        case "double":
                                        case "float":
                                            paramTypes.add("number");
                                            break;
                                        case "boolean":
                                            paramTypes.add("boolean");
                                            break;
                                        default:
                                            paramTypes.add("object");
                                    }

                                    paramNames.add(param.getSimpleName().toString());
                                }
                                
                                //get target package
                                String targetPackage;
                                if (method.getAnnotation(Namespace.class) == null) {
                                    targetPackage = classPackage;
                                } else {
                                    targetPackage = method.getAnnotation(Namespace.class).value();
                                    if (! targetPackage.equals("")) {
                                        targetPackage = "." + targetPackage;
                                    }
                                }
                                
                                //first check if the method conforms to the same schema it did before
                                String formerMethodSchemaFile = "";
                                if (formerEntitySchema != null) { //null if there was no such entity schema
                                    JsonNode eventTypes = formerEntitySchema.get("eventTypes");
                                    for (JsonNode node : eventTypes) {
                                        if (node.get(methodName) != null) {
                                            formerMethodSchemaFile = node.get(methodName).textValue();
                                            break;
                                        }
                                    }
                                    
                                    if (! formerMethodSchemaFile.equals("")) {
                                        String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                + formerMethodSchemaFile.replace('.', File.separatorChar) + ".json";
                                        JsonNode root = mapper.readTree(FileSystems.getDefault().getPath(pkg).toFile());
                                        
                                        if (targetPackage.equals(formerMethodSchemaFile.substring(EVENTTYPES_BASE_PKG.length(), formerMethodSchemaFile.lastIndexOf('.')))
                                                && matches(root, paramNames, paramTypes)) {
                                            //i.e. no changes, the method matches the same schema as before
                                            entitySchema.writeStartObject();
                                            entitySchema.writeStringField(methodName, EVENTTYPES_BASE_PKG + targetPackage + "." + methodSchemaName);
                                            entitySchema.writeEndObject();
                                            continue;  
                                        } else {
                                            unlinkEntityFromMethodSchema(pkg, root, ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName, mapper, jsonFactory);
                                        }
                                    }
                                }
                                
                                //if there was no schema for this entity, or it did not contain the method, or the method did not match the schema it was linked to:
                                //go through all schemas for this methodName in targetPackage and try to find one that matches
                                String matchingSchemaFile = "";
                                JsonNode root = null;
                                String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator + 
                                    EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar);
                                //create non-existing directories
                                Files.createDirectories(FileSystems.getDefault().getPath(pkg));
                                
                                try (DirectoryStream<Path> dir = Files.newDirectoryStream(FileSystems.getDefault().getPath(pkg))) {
                                    for (Path p : dir) {
                                        if (p.getFileName().toString().equals(methodName + ".json") || p.getFileName().toString().startsWith(methodName + "_")) {
                                            //skip the one we checked before
                                            if (! formerMethodSchemaFile.equals("")) {
                                                if (p.toString().equals("src" + File.separator + "main" + File.separator + "resources" + File.separator 
                                                        + formerMethodSchemaFile.replace('.', File.separatorChar) + ".json")) {
                                                    continue;
                                                }
                                            }
                                            
                                            root = mapper.readTree(p.toFile());
                                            if (matches(root, paramNames, paramTypes)) {
                                                matchingSchemaFile = p.getFileName().toString();
                                                break;
                                            }
                                        }
                                    }
                                } catch (IOException ioe) {
                                    //TODO hlaska
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Error reading file.");
                                }
                                
                                if (! matchingSchemaFile.equals("")) {
                                    //update methodSchemaName for this method in entity schema
                                    methodSchemaName = matchingSchemaFile.substring(0, matchingSchemaFile.length() - 5); //without file extension
                                    //and link this entity to the matching method schema
                                    boolean contains = false;
                                    for (String entity : root.findValuesAsText("usedBy")) {
                                        if (entity.equals(ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName)) {
                                            contains = true;
                                            break;
                                        }
                                    }
                                    if (! contains) {
                                        ArrayNode usedBy = (ArrayNode) root.get("usedBy");
                                        usedBy.add(ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName);
                                        ((ObjectNode)root).put("usedBy", usedBy);
                                        
                                        String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                + EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + matchingSchemaFile;
                                        Path pp = FileSystems.getDefault().getPath(p);
                                        //update the file
                                        try (JsonGenerator generator = jsonFactory.createGenerator(pp.toFile(), JsonEncoding.UTF8)) {
                                            generator.useDefaultPrettyPrinter();
                                            mapper.writeTree(generator, root);
                                        }
                                    }
                                } else { //create new
                                    try {
                                        String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                + EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + methodName + ".json";
                                        Path pp = FileSystems.getDefault().getPath(p);
                                        int suffix = 0;
                                        //find the first available file name for this method schema
                                        while (Files.exists(pp)) {
                                            suffix++;
                                            p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                    + EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                    + File.separator + methodName + "_" + suffix + ".json";
                                            pp = FileSystems.getDefault().getPath(p);
                                        }
                                        
                                        if (suffix != 0) {
                                            methodSchemaName = methodName + "_" + suffix;
                                        } //else: methodSchemaName == methodName

                                        String methodPath = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar
                                                + EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + methodSchemaName + ".json";
                                        
                                        Path methodSchemaFile = FileSystems.getDefault().getPath(methodPath);
                                        
                                        //create non-existing directories
                                        Files.createDirectories(methodSchemaFile.getParent());
                                        try (JsonGenerator methodSchema = jsonFactory.createGenerator(methodSchemaFile.toFile(), JsonEncoding.UTF8)) {
                                            methodSchema.useDefaultPrettyPrinter();

                                            methodSchema.writeStartObject();
                                            methodSchema.writeStringField("$schema", "http://json-schema.org/schema#");
                                            methodSchema.writeStringField("title", methodName);
                                            methodSchema.writeArrayFieldStart("type");
                                            methodSchema.writeString("object");
                                            methodSchema.writeEndArray();
                                            methodSchema.writeObjectFieldStart("properties");
                                            //method parameters and their types:
                                            String required = "";
                                            boolean comma = false;
                                            for (int i = 0; i < paramNames.size(); i++) {
                                                methodSchema.writeObjectFieldStart(paramNames.get(i));
                                                methodSchema.writeStringField("type", paramTypes.get(i));
                                                methodSchema.writeEndObject();

                                                if (comma) {
                                                    required += ", ";
                                                } else {
                                                    comma = true;
                                                }
                                                required += "\"" + paramNames.get(i) + "\"";
                                            }
                                            methodSchema.writeEndObject();
                                            methodSchema.writeArrayFieldStart("required");
                                            methodSchema.writeRaw(required);
                                            methodSchema.writeEndArray();
                                            methodSchema.writeBooleanField("additionalProperties", false);

                                            methodSchema.writeArrayFieldStart("usedBy");
                                            methodSchema.writeString(ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName);
                                            methodSchema.writeEndArray();

                                            methodSchema.writeEndObject();
                                        }
                                    } catch (IOException ex) {
                                        messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + methodSchemaName + ".json");
                                    }
                                }
                                
                                //link method to its schema
                                entitySchema.writeStartObject();
                                entitySchema.writeStringField(methodName, EVENTTYPES_BASE_PKG + targetPackage + "." + methodSchemaName);
                                entitySchema.writeEndObject();
                            }
                        }
                        
                        entitySchema.writeEndArray();
                        entitySchema.writeEndObject();
                        
                        //take care of deleted methods: remove the link to this entity from their schemas
                        if (formerEntitySchema != null) {
                            JsonNode eventTypes = formerEntitySchema.get("eventTypes");
                            for (JsonNode node : eventTypes) {
                                Iterator<String> it = node.fieldNames();
                                String name = it.next();
                                if (! methodNames.contains(name)) {
                                    String pathToFormerMethodSchema = node.get(name).textValue();
                                    String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                            + pathToFormerMethodSchema.replace('.', File.separatorChar) + ".json";
                                    JsonNode root = mapper.readTree(FileSystems.getDefault().getPath(pkg).toFile());
                                    
                                    unlinkEntityFromMethodSchema(pkg, root, ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName, mapper, jsonFactory);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + entitySchemaName + ".json");
                }
            }
            
            //update lastBuildTime (used for tracking changes in class files)
            Properties properties = new Properties();
            properties.setProperty("lastBuildTime", String.valueOf(new Date().getTime()));
            try {
                properties.store(new FileOutputStream("src/main/resources/config.properties"), null);
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file src/main/resources/config.properties");
            }
        }
        
        if (firstRound) {
            if (! Files.exists(FileSystems.getDefault().getPath("src" + File.separator + "main" + File.separator + "java" +
                    File.separator + CLASSES_BASE_PKG + File.separator + LOGGER + ".java"))) {
                String loggerContent = "package LOGGER;\n\n"
                                     + "import java.util.HashMap;\n"
                                     + "import java.util.Map;\n\n"
                                     + "public class " + LOGGER + " {\n\n"
                                     + "    public static void log(String[] names, Object... values) {\n"
                                     + "        Map<String, Object> map = new HashMap<>();\n\n"
                                     + "        for (int i = 0; i < names.length; i++) {\n"
                                     + "            map.put(names[i], values[i]);\n"
                                     + "        }\n"
                                     + "    }\n"
                                     + "}\n";
                try {
                    filer.createSourceFile(CLASSES_BASE_PKG + "." + LOGGER)
                            .openWriter()
                            .append(loggerContent)
                            .close();
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + CLASSES_BASE_PKG + "." + LOGGER);
                }
            }
            
            String entitiesDir = "src" + File.separator + "main" + File.separator + "resources" + File.separator + ENTITIES_BASE_PKG;
            final int entitiesDirLength = entitiesDir.length();
            final List<String> processed = processedClasses;
            try {
                Files.walkFileTree(FileSystems.getDefault().getPath(entitiesDir), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String filename = path.getFileName().toString();
                        
                        if (filename.toLowerCase().endsWith(".json")) {
                            String classPackage = ""; //".cz.muni.fi" for "LOGGER.cz.muni.fi.L_Entity.java", or "" for "LOGGER.L_Entity.java"
                            String dir = path.toString().substring(0, path.toString().length() - filename.length() - 1);
                            if (dir.length() != entitiesDirLength) {
                                classPackage = "." + dir.substring(entitiesDirLength + 1).replace(File.separatorChar, '.');
                            }
                            
                            if (processed.contains(CLASSES_BASE_PKG + classPackage + "." + CLASS_PREFIX + filename.substring(0, filename.length() - 5))) {
                                return FileVisitResult.CONTINUE;
                            }
                            
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode entitySchemaRoot;
                            try {
                                entitySchemaRoot = mapper.readTree(path.toFile());
                                
                                String className = CLASS_PREFIX + filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                                
                                String classBeginning = "package " + CLASSES_BASE_PKG + classPackage + ";\n\n";
                                if (! classPackage.equals("")) {
                                    classBeginning += "import " + CLASSES_BASE_PKG + "." + LOGGER + ";\n";
                                }
                                
                                String classContent = "\npublic class " + className + " extends " + LOGGER + " {\n";
                                
                                ArrayNode eventTypes = (ArrayNode)(entitySchemaRoot.get("eventTypes"));
                                //delete entities with no methods
                                if (eventTypes.size() == 0) {
                                    Files.delete(path);
                                    return FileVisitResult.CONTINUE;
                                }
                                
                                //generate methods for all method schemas
                                boolean namespaceImport = false;
                                for (int i = 0; i < eventTypes.size(); i++) {
                                    JsonNode methodNode = eventTypes.get(i);
                                    Iterator<String> it = methodNode.fieldNames();
                                    String methodName = it.next();
                                    String schemaPackage = methodNode.get(methodName).textValue();
                                    
                                    String methodSchemaPath = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                            + schemaPackage.replace('.', File.separatorChar) + ".json";
                                    Path methodSchema = FileSystems.getDefault().getPath(methodSchemaPath);
                                    JsonNode methodSchemaRoot = mapper.readTree(methodSchema.toFile());
                                    JsonNode parameters = methodSchemaRoot.get("properties");
                                    Iterator<String> paramsIterator = parameters.fieldNames();
                                    boolean putComma = false;
                                    
                                    //add @Namespace if classPackage != targetPackage
                                    String targetPackage = schemaPackage.substring(EVENTTYPES_BASE_PKG.length(), schemaPackage.lastIndexOf('.'));
                                    if (! classPackage.equals(targetPackage)) {
                                        if (! namespaceImport) {
                                            classBeginning += "import cz.muni.fi.annotation.Namespace;\n";
                                            namespaceImport = true;
                                        }
                                        if (targetPackage.length() > 0) {
                                            targetPackage = targetPackage.substring(1);
                                        }
                                        classContent += "\n    @Namespace(\"" + targetPackage + "\")";
                                    }
                                    
                                    classContent += "\n    public static void " + methodName + "(";
                                    String strings = "";
                                    String args = "";
                                    while (paramsIterator.hasNext()) {
                                        if (putComma) {
                                            classContent += ", ";
                                            strings += ",";
                                        } else {
                                            putComma = true;
                                        }
                                        String paramName = paramsIterator.next();
                                        JsonNode param = parameters.get(paramName);
                                        switch (param.get("type").textValue()) {
                                            case "string":
                                                classContent += "String ";
                                                break;
                                            case "integer":
                                                classContent += "int ";
                                                break;
                                            case "number":
                                                classContent += "double ";
                                                break;
                                            case "boolean":
                                                classContent += "boolean ";
                                                break;
                                            default:
                                                classContent += "Object ";
                                        }
                                        classContent += paramName;
                                        strings += "\"" + paramName + "\"";
                                        args += ", " + paramName;
                                    }
                                    classContent += ") {\n" + "        log(new String[]{" + strings + "}" + args + ");\n" + "    }\n";
                                }
                                
                                classContent += "}\n";
                                
                                JavaFileObject file = filer.createSourceFile(CLASSES_BASE_PKG + classPackage + "." + className);
                            
                                file.openWriter()
                                        .append(classBeginning + classContent)
                                        .close();

                                newClasses.add(CLASSES_BASE_PKG + classPackage + "." + className);
                            } catch (IOException ex) {
                                messager.printMessage(Diagnostic.Kind.ERROR, filename + ": unexpected format of schema");
                            }
                        }
                        
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                //TODO
            }
            
            firstRound = false;
        }
        
        return true;
    }
    
    /*
     * Validates method parameters against JSON Schema.
     * 
     * @return true if all parameter names and types match those in the schema, false otherwise
     */
    private boolean matches(JsonNode schema, List<String> paramNames, List<String> paramTypes) {
        JsonNode properties = schema.get("properties");
        Iterator<String> paramsIterator = properties.fieldNames();
        boolean ok = true;
        int i = 0;
        
        while (paramsIterator.hasNext()) {
            if (i+1 > paramNames.size()) {
                ok = false;
                break;
            }

            String param = paramsIterator.next();
            if (! param.equals(paramNames.get(i))) {
                ok = false;
                break;
            } else {
                JsonNode type = properties.get(param);
                if (! type.get("type").textValue().equals(paramTypes.get(i))) {
                    ok = false;
                    break;
                }
            }
            i++;
        }
        
        if (ok && (i == paramNames.size())) {
            return true;
        } else {
            return false;
        }
    }
    
    /*
     * Removes reference to entity from method schema.
     */
    private void unlinkEntityFromMethodSchema(String path, JsonNode schema, String entity, ObjectMapper mapper, JsonFactory jsonFactory) throws IOException {
        ArrayNode usedBy = (ArrayNode)(schema.get("usedBy"));
        for (int j = 0; j < usedBy.size(); j++) {
            JsonNode node = usedBy.get(j);
            if (node.textValue().equals(entity)) {
                usedBy.remove(j);
                break;
            }
        }

        if (usedBy.size() == 0) { //delete the schema if no entities reference it anymore
            Files.delete(FileSystems.getDefault().getPath(path));
        } else {
            ((ObjectNode)schema).put("usedBy", usedBy);
            //update the file
            try (JsonGenerator generator = jsonFactory.createGenerator(FileSystems.getDefault().getPath(path).toFile(), JsonEncoding.UTF8)) {
                generator.useDefaultPrettyPrinter();
                mapper.writeTree(generator, schema);
            }
        }
    }
}