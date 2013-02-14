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

/**
 *
 * @author Andrejka
 */
@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO normalne komentare
    
    //TODO premenovat premenne, ucesat kod
    
    private Filer filer;
    private Messager messager;
    
    private static final String SCH_ENTITIES_BASE_PKG = "ENTITIES";
    private static final String SCH_EVENTTYPES_BASE_PKG = "SCHEMAS";
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
                        
                        //generovat schemu, len ak je trieda zmenena (zatial kontrolovat takto, potom to este premysliet):
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
        } else { //last Round - vygenerujeme schemy zo vsetkych tried okrem tych novych
            for (Element element : schemasToGenerate) {
                String pack = element.getEnclosingElement().toString();
                String elementName = element.getSimpleName().toString();
                
                if (newClasses.contains(pack + "." + elementName) || elementName.equals(LOGGER)) {
                    continue;
                }
                
                String entitySchemaName = elementName.substring(CLASS_PREFIX.length()); //oddelat prefix z nazvu triedy
                try {
                    String classPackage = pack.substring(CLASSES_BASE_PKG.length());
                    String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + 
                            SCH_ENTITIES_BASE_PKG + classPackage.replace('.', File.separatorChar) + 
                            File.separatorChar + entitySchemaName + ".json";
                    Path file = FileSystems.getDefault().getPath(path);
                    
                    //vytvorit neexistujuce adresare
                    Files.createDirectories(file.getParent());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    
                    //este pred prepisanim schemy pre tu entitu si zapamatam jej povodny obsah - budem to potrebovat neskor
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
                                
                                //nazov metody a jej schemicky sa nemusi zhodovat; mapovanie bude v scheme pre entitu
                                //(aby sa to trochu menej plietlo: "schemicka" = pre metodu, "schema" = pre entitu)
                                String methodName = method.getSimpleName().toString();
                                String methodSchemaName = method.getSimpleName().toString();
                                //zakazat pretazovanie metod
                                if (methodNames.contains(methodName)) {
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Method overloading not allowed here", e);
                                } else {
                                    methodNames.add(methodName);
                                }
                                
                                //ziskat vsetky parametre aj s typmi
                                List<String> paramNames = new ArrayList<>();
                                List<String> paramTypes = new ArrayList<>();
                                for (VariableElement param : method.getParameters()) {
                                    String typeFull = param.asType().toString();
                                    String type = typeFull.substring(typeFull.lastIndexOf(".") + 1); //kvoli fully qualified menam
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
                                
                                //ziskat balik schemicky
                                String targetPackage;
                                if (method.getAnnotation(Namespace.class) == null) {
                                    targetPackage = classPackage;
                                } else {
                                    //anotacia ma prednost pred balikom triedy
                                    targetPackage = method.getAnnotation(Namespace.class).value();
                                    if (! targetPackage.equals("")) {
                                        targetPackage = "." + targetPackage;
                                    }
                                }
                                
                                //skontrolovat ako prvu tu schemicku, ktoru ma namapovanu v entite
                                String formerMethodSchemaFile = "";
                                if (formerEntitySchema != null) { //null je v pripade, ze este neexistovala ta schema
                                    JsonNode eventTypes = formerEntitySchema.get("eventTypes");
                                    for (JsonNode n : eventTypes) {
                                        if (n.get(methodName) != null) { //mame ten mapping, co chceme
                                            formerMethodSchemaFile = n.get(methodName).textValue();
                                        }
                                    }
                                    //skontrolovat, ci sa s touto schemickou zhoduje dana metoda
                                    if (! formerMethodSchemaFile.equals("")) {
                                        //formerMethodSchemaFile = SCHEMAS.cz.muni.fi.a.method2_1
                                        //targetPackage = .cz.muni.fi.a
                                        if (targetPackage.equals(formerMethodSchemaFile.substring(SCH_EVENTTYPES_BASE_PKG.length(), formerMethodSchemaFile.lastIndexOf('.')))) {
                                            //nacitaj tu schemicku a skontroluj obsah
                                            String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                    + formerMethodSchemaFile.replace('.', File.separatorChar) + ".json";
                                            JsonNode root = mapper.readTree(FileSystems.getDefault().getPath(pkg).toFile());
                                            
                                            if (matches(root, paramNames, paramTypes)) {
                                                //zhodovala sa, to znamena, ze existuje pre nu schema, a tu schemu ma uz namapovanu v entite,
                                                // tj uz s tym netreba nic robit, len ju zapisat; potom chod na dalsiu metodu
                                                entitySchema.writeStartObject();
                                                entitySchema.writeStringField(methodName, SCH_EVENTTYPES_BASE_PKG + targetPackage + "." + methodSchemaName);
                                                entitySchema.writeEndObject();
                                                continue;
                                            } else {
                                                //nezhoduje sa, takze z tej namapovanej odstranit tuto entitu (pripadne ju aj zmazat)
                                                unlinkEntityFromMethodSchema(pkg, root, SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName, mapper, jsonFactory);
                                            }
                                        } else {
                                            //zmaz z tej povodne namapovanej tuto entitu, lebo nesedi
                                            String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                    + formerMethodSchemaFile.replace('.', File.separatorChar) + ".json";
                                            JsonNode root = mapper.readTree(FileSystems.getDefault().getPath(pkg).toFile());
                                            
                                            unlinkEntityFromMethodSchema(pkg, root, SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName, mapper, jsonFactory);
                                        }
                                    }
                                }
                                
                                //ak neexistovala schema pre entitu, alebo v nej nebola dana metoda, alebo nesedela s povodne priradenou schemickou:
                                //v tom baliku prehladat vsetky schemicky pre metodu s tymto menom (tj tie, co zacinaju na jej meno)
                                String matchingSchemaFile = "";
                                JsonNode root = null;
                                String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator + 
                                    SCH_EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar);
                                //vytvorit neexistujuce adresare
                                Files.createDirectories(FileSystems.getDefault().getPath(pkg));
                                
                                try (DirectoryStream<Path> dir = Files.newDirectoryStream(FileSystems.getDefault().getPath(pkg))) {
                                    for (Path p : dir) {
                                        if (p.getFileName().toString().equals(methodName + ".json") || p.getFileName().toString().startsWith(methodName + "_")) {
                                            //skontroluj, ci sa struktura metody (=parametre) zhoduje s touto schemickou
                                            
                                            //vynechat tu, co mal napisanu ako mapping v entite - tu sme uz kontrolovali
                                            if (! formerMethodSchemaFile.equals("")) { //ak vobec v entite nieco k tomu bolo
                                                if (p.toString().equals("src" + File.separator + "main" + File.separator + "resources" + File.separator 
                                                        + formerMethodSchemaFile.replace('.', File.separatorChar) + ".json")) {
                                                    continue;
                                                }
                                            }
                                            
                                            root = mapper.readTree(p.toFile());
                                            if (matches(root, paramNames, paramTypes)) {
                                                matchingSchemaFile = p.getFileName().toString(); //zhodovala sa, vyskocit z cyklu
                                                break;
                                            }
                                        }
                                    }
                                } catch (IOException ioe) {
                                    //TODO hlaska
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Error reading file.");
                                }
                                
                                if (! matchingSchemaFile.equals("")) { //ak sa zhodovala dana metoda s existujucou schemickou
                                    //aktualizuj mapovanie v scheme pre entitu
                                    //potom negenerovat schemicku (iba zapiseme odkaz na tuto do schemy pre entitu)
                                    methodSchemaName = matchingSchemaFile.substring(0, matchingSchemaFile.length() - 5); //bez .json
                                    //a pridaj tuto entitu do zoznamu v schemicke
                                    //v root mam nacitany obsah schemicky, tak tam len zmenim usedbyentities
                                    boolean contains = false;
                                    for (String entity : root.findValuesAsText("usedByEntities")) {
                                        if (entity.equals(SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName)) {
                                            contains = true;
                                            break;
                                        }
                                    }
                                    if (! contains) {
                                        ArrayNode usedByEntities = (ArrayNode) root.get("usedByEntities");
                                        usedByEntities.add(SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName);
                                        ((ObjectNode)root).put("usedByEntities", usedByEntities);
                                        
                                        String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                + SCH_EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + matchingSchemaFile;
                                        Path pp = FileSystems.getDefault().getPath(p);
                                        //zapisat to naspat do toho suboru
                                        try (JsonGenerator generator = jsonFactory.createGenerator(pp.toFile(), JsonEncoding.UTF8)) {
                                            generator.useDefaultPrettyPrinter();
                                            mapper.writeTree(generator, root);
                                        }
                                    }
                                } else { //ak taka schemicka este nie je, vytvor pre nu novy subor
                                    try {
                                        String p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                + SCH_EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + methodName + ".json";
                                        Path pp = FileSystems.getDefault().getPath(p);
                                        int suffix = 0;
                                        //najdeme neobsadeny nazov suboru (ako prve skusame bez pripony, tj. p = ...methodName.json)
                                        while (Files.exists(pp)) {
                                            suffix++;
                                            p = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                                    + SCH_EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                    + File.separator + methodName + "_" + suffix + ".json";
                                            pp = FileSystems.getDefault().getPath(p);
                                        }
                                        
                                        if (suffix != 0) {
                                            methodSchemaName = methodName + "_" + suffix;
                                        } //inak ostava methodSchemaName zhodne s methodName

                                        String methodPath = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar
                                                + SCH_EVENTTYPES_BASE_PKG + targetPackage.replace('.', File.separatorChar)
                                                + File.separator + methodSchemaName + ".json";
                                    
                                        //vygenerovat obsah schemicky:
                                        Path methodFile = FileSystems.getDefault().getPath(methodPath);

                                        //vytvorit neexistujuce adresare
                                        Files.createDirectories(methodFile.getParent());
                                        try (JsonGenerator methodSchema = jsonFactory.createGenerator(methodFile.toFile(), JsonEncoding.UTF8)) {
                                            methodSchema.useDefaultPrettyPrinter();

                                            methodSchema.writeStartObject();
                                            methodSchema.writeStringField("$schema", "http://json-schema.org/schema#");
                                            methodSchema.writeStringField("title", methodName);
                                            methodSchema.writeArrayFieldStart("type");
                                            methodSchema.writeString("object");
                                            methodSchema.writeEndArray();
                                            methodSchema.writeObjectFieldStart("properties");
                                            //teraz vsetky parametre s typmi:
                                            String required = ""; //aby sme nemuseli znovu prechadzat paramNames
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

                                            methodSchema.writeArrayFieldStart("usedByEntities");
                                            methodSchema.writeString(SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName);
                                            methodSchema.writeEndArray();

                                            methodSchema.writeEndObject();
                                        }
                                    } catch (IOException ex) {
                                        messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + methodSchemaName + ".json");
                                    }
                                }
                                
                                //poznamenat si pouzitie tejto metody do schemy pre entitu; schvalne az teraz, pretoze medzitym sa
                                //  mohla vygenerovat schemicka s inym nazvom, ako mala povodna metoda
                                entitySchema.writeStartObject();
                                entitySchema.writeStringField(methodName, SCH_EVENTTYPES_BASE_PKG + targetPackage + "." + methodSchemaName);
                                entitySchema.writeEndObject();
                            }
                        }
                        
                        entitySchema.writeEndArray();
                        entitySchema.writeEndObject();
                        
                        //este na zaver upratat:
                        //zo vsetkych schemiciek povodne namapovanych na metody, ktore uz teraz neexistuju, zmazat aktualnu entitu
                        if (formerEntitySchema != null) { //ak existovala povodne schema pre entitu
                            JsonNode eventTypes = formerEntitySchema.get("eventTypes");
                            for (JsonNode n : eventTypes) {
                                Iterator<String> it = n.fieldNames();
                                String name = it.next();
                                if (! methodNames.contains(name)) { //ak uz tato metoda nie je v skumanej triede
                                    //chod na tento subor
                                    String pathToFormerMethodSchema = n.get(name).textValue();
                                    //a zmaz odtial entitu z usedByEntities
                                    String pkg = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                            + pathToFormerMethodSchema.replace('.', File.separatorChar) + ".json";
                                    JsonNode root = mapper.readTree(FileSystems.getDefault().getPath(pkg).toFile());

                                    unlinkEntityFromMethodSchema(pkg, root, SCH_ENTITIES_BASE_PKG + classPackage + "." + entitySchemaName, mapper, jsonFactory);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + entitySchemaName + ".json");
                }
            }
            
            //a uplne na zaver si zapisat cas posledneho buildu
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
                                     + "    public static Map<String, Object> log(String[] names, Object... values) {\n"
                                     + "        Map<String, Object> map = new HashMap<>();\n\n"
                                     + "        for (int i = 0; i < names.length; i++) {\n"
                                     + "            map.put(names[i], values[i]);\n"
                                     + "        }\n\n"
                                     + "        return map;\n"
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
            
            String entitiesDir = "src" + File.separator + "main" + File.separator + "resources" + File.separator + SCH_ENTITIES_BASE_PKG;
            final int entitiesDirPrefixLength = entitiesDir.length();
            final List<String> processed = processedClasses;
            try {
                Files.walkFileTree(FileSystems.getDefault().getPath(entitiesDir), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String filename = path.getFileName().toString();
                        
                        if (filename.toLowerCase().endsWith(".json")) {
                            String classPackage = ""; //".cz.muni.fi" pre "LOGGER.cz.muni.fi.L_Entity.java" alebo "" pre "LOGGER.L_Entity.java"
                            {
                                String dir = path.toString().substring(0, path.toString().length() - filename.length() - 1);
                                if (dir.length() != entitiesDirPrefixLength) {
                                    classPackage = "." + dir.substring(entitiesDirPrefixLength + 1).replace(File.separatorChar, '.');
                                }
                            }
                            
                            if (processed.contains(CLASSES_BASE_PKG + classPackage + "." + CLASS_PREFIX + filename.substring(0, filename.length() - 5))) {
                                return FileVisitResult.CONTINUE;
                            }
                            
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode entitySchemaRoot;
                            try {
                                entitySchemaRoot = mapper.readTree(path.toFile());
                                
                                //meno triedy je L_menoSuboruSoSchemouPreEntitu (bez pripony)
                                String className = CLASS_PREFIX + filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                                
                                String classBeginning = "package " + CLASSES_BASE_PKG + classPackage + ";\n\n";
                                if (! classPackage.equals("")) {
                                    classBeginning += "import " + CLASSES_BASE_PKG + "." + LOGGER + ";\n";
                                }
                                
                                String classContent = "\npublic class " + className + " extends " + LOGGER + " {\n";
                                
                                ArrayNode eventTypes = (ArrayNode)(entitySchemaRoot.get("eventTypes"));
                                //ak entita neodkazuje na ziadne metody, len ju zmaz, negeneruj z nej ziadnu triedu
                                //(neviem sice, kedy by to mohlo nastat, ale spravat by sa to malo takto.)
                                if (eventTypes.size() == 0) {
                                    Files.delete(path);
                                    return FileVisitResult.CONTINUE;
                                }
                                
                                //prejdi vsetky subory so schemickami a generuj metody
                                boolean namespaceImport = false;
                                for (int i = 0; i < eventTypes.size(); i++) {
                                    JsonNode methodNode = eventTypes.get(i);
                                    Iterator<String> it = methodNode.fieldNames();
                                    String methodName = it.next();
                                    String schemaPack = methodNode.get(methodName).textValue();
                                    
                                    String methodSchemaPath = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                            + schemaPack.replace('.', File.separatorChar) + ".json";
                                    Path methodSchema = FileSystems.getDefault().getPath(methodSchemaPath);
                                    JsonNode methodSchemaRoot = mapper.readTree(methodSchema.toFile());
                                    JsonNode parameters = methodSchemaRoot.get("properties");
                                    Iterator<String> paramsIterator = parameters.fieldNames();
                                    boolean putComma = false;
                                    
                                    //pridat @Namespace ak dana schemicka nie je v rovnakom baliku ako trieda
                                    String targetPack = schemaPack.substring(SCH_EVENTTYPES_BASE_PKG.length(), schemaPack.lastIndexOf('.'));
                                    if (! classPackage.equals(targetPack)) {
                                        if (! namespaceImport) {
                                            classBeginning += "import cz.muni.fi.annotation.Namespace;\n";
                                            namespaceImport = true;
                                        }
                                        if (targetPack.length() > 0) {
                                            targetPack = targetPack.substring(1);
                                        }
                                        classContent += "\n    @Namespace(\"" + targetPack + "\")";
                                    }
                                    
                                    //a vypisat metodu
                                    classContent += "\n    public static void " + methodName + "(";
                                    String strings = "";
                                    String args = "";
                                    while (paramsIterator.hasNext()) {
                                        if (putComma) {
                                            classContent += ", ";
                                            strings += ",";
                                            //schvalne tu nie je 'args += ", ";' - robilo to problemy pri bezparametrickych metodach.
                                            //take by sice nemali byt, ale uzivatelia su zakerni
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
                                    //zavriet tu zatvorku za parametrami metody, dopisat telo metody
                                    classContent += ") {\n" + "        log(new String[]{" + strings + "}" + args + ");\n" + "    }\n";
                                }
                                
                                //potom na zaver dokonci triedu a zapis ju do suboru
                                classContent += "}\n";
                                
                                JavaFileObject file = filer.createSourceFile(CLASSES_BASE_PKG + classPackage + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!
                            
                                file.openWriter()
                                        .append(classBeginning + classContent)
                                        .close();

                                newClasses.add(CLASSES_BASE_PKG + classPackage + "." + className); //poznacit si, ktoru sme vygenerovali teraz
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
    
    private boolean matches(JsonNode root, List<String> paramNames, List<String> paramTypes) {
        JsonNode properties = root.get("properties");
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
        
        if (ok && (i == paramNames.size())) { //sice mohlo sediet prvych i, ale nemuseli to byt vsetky
            return true;
        } else {
            return false;
        }
    }
    
    private void unlinkEntityFromMethodSchema(String pkg, JsonNode root, String entity, ObjectMapper mapper, JsonFactory jsonFactory) throws IOException {
        ArrayNode usedByUpdated = (ArrayNode)(root.get("usedByEntities"));
        for (int j = 0; j < usedByUpdated.size(); j++) {
            JsonNode node = usedByUpdated.get(j);

            if (node.textValue().equals(entity)) {
                usedByUpdated.remove(j);
            }
        }

        if (usedByUpdated.size() == 0) {
            Files.delete(FileSystems.getDefault().getPath(pkg));
        } else {
            ((ObjectNode)root).put("usedByEntities", usedByUpdated);
            //zapisat to naspat do toho suboru
            try (JsonGenerator generator = jsonFactory.createGenerator(FileSystems.getDefault().getPath(pkg).toFile(), JsonEncoding.UTF8)) {
                generator.useDefaultPrettyPrinter();
                mapper.writeTree(generator, root);
            }
        }
    }
}