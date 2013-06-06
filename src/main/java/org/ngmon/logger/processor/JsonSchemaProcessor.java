package org.ngmon.logger.processor;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ngmon.logger.annotation.Namespace;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

/**
 * Generates JSON Schemas for @Namespace-annotated classes and vice versa.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    private Filer filer;
    private Messager messager;
    
    private static final String EVENTS_BASE_PKG = "log_events";
    private static final String CONFIG_PATH = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + "config.properties";
    
    private static final String IMPORT_NAMESPACE = "import org.ngmon.logger.annotation.Namespace;\n";
    private static final String IMPORT_ABSTRACTNAMESPACE = "import org.ngmon.logger.core.AbstractNamespace;\n";
    
    private long lastBuildTime = 0;
    private boolean firstRound = true;
    private List<Element> schemasToGenerate = new ArrayList<>();
    private List<String> newClasses = new ArrayList<>();
    
    private static final Map<String, String> TYPES;
    static {
        Map<String, String> types = new HashMap<>();
        types.put("string", "String");
        types.put("String", "string");
        types.put("int", "integer");
        types.put("integer", "int");
        types.put("double", "number");
        types.put("float", "number");
        types.put("number", "double");
        types.put("boolean", "boolean");
        TYPES = Collections.unmodifiableMap(types);
    };
    
    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
        
        Properties properties = new Properties();
        try {
            properties.load(new FileReader(CONFIG_PATH));
            lastBuildTime = Long.parseLong(properties.getProperty("lastBuildTime"));
        } catch (IOException ex) {
        }
    }

    @Override
    public boolean process(Set annotations, RoundEnvironment env) {
        if (firstRound) {
            List<String> existingNamespaces = checkExistingNamespaces(env);
            generateNamespaces(existingNamespaces);
            firstRound = false;
        }
        
        if (env.processingOver()) { //last round - generate schemas for all classes (except for those that have just been created)
            generateSchemas();
            
            //update lastBuildTime (used for tracking changes in class files)
            Properties properties = new Properties();
            properties.setProperty("lastBuildTime", String.valueOf(new Date().getTime()));
            try {
                Files.createDirectories(FileSystems.getDefault().getPath(CONFIG_PATH).getParent());
                properties.store(new FileWriter(CONFIG_PATH), null);
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + CONFIG_PATH);
            }
        }
        
        return true;
    }
    
    /**
     * Returns the fully qualified name of this Element.
     */
    private String getFQN(Element classElement) {
        return classElement.getEnclosingElement().toString() + "." + classElement.getSimpleName().toString();
    }
    
    /**
     * Returns JSON schema equivalent of this Java type.
     */
    private String toJSONType(String javaType) {
        String jsonType = TYPES.get(javaType);
        if (jsonType == null) {
            if (javaType.equals("float")) {
                jsonType = "number";
            } else {
                jsonType = "object";
            }
        }
        
        return jsonType;
    }
    
    /**
     * Returns Java equivalent of this JSON schema type.
     */
    private String toJavaType(String jsonType) {
        String javaType = TYPES.get(jsonType);
        if (javaType == null) {
            javaType = "Object";
        }
        
        return javaType;
    }
    
    /**
     * Checks whether the requirements set for namespaces are met and generates a compilation error if they are not.
     * 
     * @return the list of existing namespaces
     */
    private List<String> checkExistingNamespaces(RoundEnvironment env) {
        List<String> namespaces = new ArrayList<>();
        for (Element element : env.getElementsAnnotatedWith(Namespace.class)) { //for all @Namespace-annotated classes
            String elementFQN = getFQN(element);
            namespaces.add(elementFQN);
            //get the time of the last modification to the file
            String path = "src" + File.separatorChar + "main" + File.separatorChar + "java" + File.separatorChar + EVENTS_BASE_PKG + File.separatorChar
                + elementFQN.replace('.', File.separatorChar) + ".java";
            Path p = FileSystems.getDefault().getPath(path);
            long lastModified;
            try {
                lastModified = Files.getLastModifiedTime(p).toMillis();
            } catch (IOException ex) {
                lastModified = new Date().getTime();
            }

            //process only classes changed since the last build
            if (lastModified > lastBuildTime) {
                Set<String> methods = new HashSet<>();
                boolean isValid = true;
                boolean nullaryConstructor = false;
                for (Element elem : element.getEnclosedElements()) {
                    switch (elem.getKind()) {
                        case CONSTRUCTOR:
                            //(nullary constructor needed for creating a new instance in LoggerFactory)
                            ExecutableElement constructor = (ExecutableElement) elem;
                            if (constructor.getParameters().isEmpty() && (! constructor.getModifiers().contains(Modifier.PRIVATE))) {
                                nullaryConstructor = true;
                            }
                            break;
                        case METHOD:
                            ExecutableElement method = (ExecutableElement) elem;
                            String methodName = method.getSimpleName().toString();

                            //forbid method overloading
                            if (methods.contains(methodName)) {
                                messager.printMessage(Diagnostic.Kind.ERROR, "Method overloading not allowed here", elem);
                                isValid = false;
                                break;
                            } else {
                                methods.add(methodName);
                            }
                            break;
                    }
                }

                if (!nullaryConstructor) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Namespaces must have a public nullary constructor.", element);
                    isValid = false;
                }

                if (isValid) {
                    schemasToGenerate.add(element);
                }
            }
        }
        
        return namespaces;
    }
    
    /**
     * Generates @Namespace-annotated classes for those JSON Schemas in package EVENTS or its subpackages that do not correspond 
     * to one of existing namespaces.
     * 
     * @param existingNSs existing namespaces
     */
    private void generateNamespaces(final List<String> existingNSs) {
        final String schemasDir = "src" + File.separator + "main" + File.separator + "resources" + File.separator + EVENTS_BASE_PKG;
        try {
            //traverse all subpackages of package EVENTS
            Files.walkFileTree(FileSystems.getDefault().getPath(schemasDir), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String filename = path.getFileName().toString();
                    
                    if (filename.toLowerCase().endsWith(".json")) {
                        String classPackage = EVENTS_BASE_PKG + "." + path.getParent().toString().substring(schemasDir.length() + 1).replace(File.separatorChar, '.');
                        
                        //do not overwrite existing namespaces
                        if (existingNSs.contains(classPackage + "." + filename.substring(0, filename.length()-5))) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        ObjectMapper mapper = new ObjectMapper();
                        
                        try {
                            JsonNode schemaRoot = mapper.readTree(path.toFile());
                            String className = filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                            
                            StringBuilder classContent = new StringBuilder();
                            if (! classPackage.equals("")) {
                                classContent.append("package ").append(classPackage).append(";\n\n");
                            }
                            
                            classContent.append(IMPORT_NAMESPACE);
                            classContent.append(IMPORT_ABSTRACTNAMESPACE);
                            
                            classContent.append("\n@Namespace\npublic class ").append(className).append(" extends AbstractNamespace {\n");
                            
                            JsonNode definitions = schemaRoot.get("definitions");

                            //generate methods
                            Iterator<String> methodsIterator = definitions.fieldNames();
                            while (methodsIterator.hasNext()) {
                                String methodName = methodsIterator.next();
                                classContent.append("\n    public AbstractNamespace ").append(methodName).append("(");
                                JsonNode method = definitions.get(methodName);
                                JsonNode parameters = method.get("properties");
                                Iterator<String> paramsIterator = parameters.fieldNames();
                                boolean putComma = false;
                                List<String> paramNames = new ArrayList<>();
                                while (paramsIterator.hasNext()) {
                                    if (putComma) {
                                        classContent.append(", ");
                                    } else {
                                        putComma = true;
                                    }
                                    String paramName = paramsIterator.next();
                                    paramNames.add(paramName);
                                    JsonNode param = parameters.get(paramName);
                                    classContent.append(toJavaType(param.get("type").textValue()));
                                    classContent.append(" ").append(paramName);
                                }
                                classContent.append(") {\n        return log(");
                                boolean comma = false;
                                for (int k = 0; k < paramNames.size(); k++) {
                                    if (comma) {
                                        classContent.append(", ");
                                    } else {
                                        comma = true;
                                    }
                                    classContent.append(paramNames.get(k));
                                }
                                classContent.append(");\n    }\n");
                            }
                            
                            classContent.append("}\n");

                            JavaFileObject file = filer.createSourceFile(classPackage + "." + className);
                            file.openWriter().append(classContent).close();

                            newClasses.add(classPackage + "." + className);
                        } catch (IOException ex) {
                            messager.printMessage(Diagnostic.Kind.ERROR, filename + ": unexpected format of schema");
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
        }
    }

    /**
     * Generates JSON Schemas for @Namespace-annotated classes changed since the last build.
     */
    private void generateSchemas() {
        for (Element element : schemasToGenerate) {
            String pack = element.getEnclosingElement().toString();
            String schemaName = element.getSimpleName().toString();

            //ignore classes that have just been generated from JSON Schemas - their schemas need no updates
            if (newClasses.contains(pack + "." + schemaName)) {
                continue;
            }

            try {
                String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar +
                        pack.replace('.', File.separatorChar) + File.separatorChar + schemaName + ".json";
                Path file = FileSystems.getDefault().getPath(path);

                //create non-existing directories
                Files.createDirectories(file.getParent());

                JsonFactory jsonFactory = new JsonFactory();
                try (JsonGenerator schema = jsonFactory.createGenerator(file.toFile(), JsonEncoding.UTF8)) {
                    schema.useDefaultPrettyPrinter();

                    schema.writeStartObject();
                    schema.writeStringField("$schema", "http://json-schema.org/schema#");
                    schema.writeStringField("title", schemaName);
                    schema.writeStringField("type", "object");

                    schema.writeArrayFieldStart("oneOf");

                    Map<String, Map<String,String>> methods = new HashMap<>();
                    for (Element e : element.getEnclosedElements()) {
                        if (e.getKind() == ElementKind.METHOD) {
                            ExecutableElement method = (ExecutableElement) e;
                            String methodName = method.getSimpleName().toString();

                            schema.writeStartObject();
                            schema.writeStringField("$ref", "#/definitions/" + methodName);
                            schema.writeEndObject(); //end {"$ref":"#/definitions/GET"}

                            //get parameter names and types
                            Map<String, String> params = new HashMap<>();
                            for (VariableElement param : method.getParameters()) {
                                String typeFull = param.asType().toString();
                                String type = typeFull.substring(typeFull.lastIndexOf(".") + 1);
                                params.put(param.getSimpleName().toString(), toJSONType(type));
                            }

                            methods.put(methodName, params);
                        }
                    }

                    schema.writeEndArray(); //end oneOf
                    schema.writeObjectFieldStart("definitions");

                    for (Map.Entry<String, Map<String, String>> method : methods.entrySet()) {
                        schema.writeObjectFieldStart(method.getKey());
                        schema.writeObjectFieldStart("properties");
                        for (Map.Entry<String, String> param : method.getValue().entrySet()) {
                            schema.writeObjectFieldStart(param.getKey());
                            schema.writeStringField("type", param.getValue());
                            schema.writeEndObject(); //end param
                        }
                        schema.writeEndObject(); //end properties
                        schema.writeArrayFieldStart("required");
                        for (String param : method.getValue().keySet()) {
                            schema.writeString(param);
                        }
                        schema.writeEndArray(); //end required
                        schema.writeBooleanField("additionalProperties", false);
                        schema.writeEndObject(); //end method
                    }

                    schema.writeEndObject(); //end of definitions
                    schema.writeEndObject(); //end of schema
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + schemaName + ".json");
            }
        }
    }
}