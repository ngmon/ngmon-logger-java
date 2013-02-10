package cz.muni.fi.processor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
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
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

/**
 *
 * @author Andrejka
 */
@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO normalne komentare
    
    private Filer filer;
    private Messager messager; //TODO warningy pri chybach? (neocakavana struktura zdrojakov atd.)
    
    private static final String SCH_ENTITIES_BASE_PKG = "ENTITIES";
    private static final String SCH_EVENTTYPES_BASE_PKG = "SCHEMAS";
    private static final String CLASSES_BASE_PKG = "LOGGER";
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
            //TODO
        }
    }

    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        List<String> processedClasses = new ArrayList<>();
        
        if (! env.processingOver()) { //s najdenymi triedami nic zatial nerob, az na konci; iba si ich zapamataj
            if (firstRound) {
                for (Element element : env.getRootElements()) {
                    String pack = element.getEnclosingElement().toString();
                    
                    if (pack.equals(CLASSES_BASE_PKG) || pack.startsWith(CLASSES_BASE_PKG + ".")) { //TODO
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
                
                if (newClasses.contains(pack + "." + elementName)) {
                    continue;
                }
                
                String schemasPack = SCH_EVENTTYPES_BASE_PKG + pack.substring(CLASSES_BASE_PKG.length());
                String entitySchemaName = elementName.substring(CLASS_PREFIX.length()); //oddelat prefix z nazvu triedy
                String entitySchemaContent = "{\n"
                                             + "    \"$schema\": \"http://json-schema.org/schema#\",\n"
                                             + "    \"title\": \"" + entitySchemaName + "\",\n"
                                             + "    \"eventTypes\": [\n";

                
                boolean putComma = false;
                for (Element e: element.getEnclosedElements()) {
                    if ((e.getKind() == ElementKind.METHOD) && e.getModifiers().contains(Modifier.PUBLIC)) {
                        ExecutableElement method = (ExecutableElement) e;
                        
                        //nazov metody a jej schemicky sa nemusi zhodovat; mapovanie bude v scheme pre entitu
                        //(aby sa to trochu menej plietlo: "schemicka" = pre metodu, "schema" = pre entitu)
                        String methodName = method.getSimpleName().toString();
                        String methodSchemaName = method.getSimpleName().toString();
                        
                        //TODO messager.error - zakazat pretazovanie metod, lebo by s tym bola asi tak miliarda problemov
                        
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
                        
                        //ok, tu mam vsetko potrebne na to, aby som mohla kontrolovat, ci sa to zhoduje so schemickou
                        //(pocas toho sa moze zmenit methodSchemaName)
                        //TODO osefovat vsetky tie pripady metod s rovnakymi nazvami, ako to mam navrhnute na papieri
                        
                        //vygenerovat obsah schemicky po vsetkych kontrolach:
                        String methodSchemaContent = "{\n"
                                                   + "    \"$schema\": \"http://json-schema.org/schema#\",\n"
                                                   + "    \"title\": \"" + methodSchemaName + "\",\n"
                                                   + "    \"type\": [\"object\"],\n"
                                                   + "    \"properties\": {\n";
                        //teraz vsetky parametre s typmi:
                        String required = "";
                        boolean comma = false;
                        for (int i = 0; i < paramNames.size(); i++) {
                            if (comma) {
                                methodSchemaContent += ",\n";
                                required += ",";
                            } else {
                                comma = true;
                            }
                            
                            methodSchemaContent += "        \"" + paramNames.get(i) + "\": {\n"
                                                 + "            \"type\": \"" + paramTypes.get(i) + "\"\n"
                                                 + "        }";
                            required += "\"" + paramNames.get(i) + "\"";
                        }
                        methodSchemaContent += "\n"
                                             + "    },\n"
                                             + "    \"required\": [" + required + "],\n"
                                             + "    \"additionalProperties\": false\n"
                                             + "}";
                        
                        //vyrobit subor pre schemicku
                        try {
                            //TODO este doriesit tie baliky; skontrolovat
                            String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + 
                                    schemasPack.replace('.', File.separatorChar) + File.separatorChar + methodSchemaName + ".json";
                            Path file = FileSystems.getDefault().getPath(path);

                            //vytvorit neexistujuce adresare
                            Files.createDirectories(file.getParent());

                            try (BufferedWriter writer = Files.newBufferedWriter(file, Charset.defaultCharset())) {
                                writer.append(methodSchemaContent);
                            }
                        } catch (IOException ex) {
                            messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + methodSchemaName + ".json");
                        }
                        
                        //poznamenat si pouzitie tejto metody do schemy pre entitu; schvalne az teraz, pretoze medzitym sa
                        //  mohla vygenerovat schemicka s inym nazvom, ako mala povodna metoda
                        if (putComma) {
                            entitySchemaContent += ",\n";
                        } else {
                            putComma = true;
                        }
                        entitySchemaContent += "        {\"" + methodName + "\": \"" + schemasPack + "." + methodSchemaName + "\"}";
                    }
                }

                //dopisat zvysok schemy pre entitu
                entitySchemaContent += "\n    ]\n"
                                       + "}";
                
                //...a zapisat ju do suboru
                try {
                    //TODO este doriesit tie baliky; skontrolovat
                    String classPackage = pack.substring(CLASSES_BASE_PKG.length());
                    if (classPackage.equals("")) {
                        classPackage = ".";
                    }
                    String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + 
                            SCH_ENTITIES_BASE_PKG + classPackage.replace('.', File.separatorChar) + 
                            File.separatorChar + entitySchemaName + ".json";
                    Path file = FileSystems.getDefault().getPath(path);
                    
                    //vytvorit neexistujuce adresare
                    Files.createDirectories(file.getParent());
                    
                    try (BufferedWriter writer = Files.newBufferedWriter(file, Charset.defaultCharset())) {
                        writer.append(entitySchemaContent);
                    }
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + entitySchemaName + ".json");
                }
            }
            
            //a uplne na zaver si zapisat cas posledneho buildu
            Properties properties = new Properties();
            properties.setProperty("lastBuildTime", String.valueOf(new Date().getTime()));
            try {
                properties.store(new FileOutputStream("src/main/resources/config.properties"), null);
            } catch (IOException ex) {
                //TODO
            }
        }
        
        
        if (firstRound) {
            //TODO ako prve vygenerovat LOGGER.Logger, ak tam este nie je?
            
            String entitiesDir = "src" + File.separator + "main" + File.separator + "resources" + File.separator + SCH_ENTITIES_BASE_PKG;
            final int entitiesDirPrefixLength = entitiesDir.length();
            final List<String> processed = processedClasses; //inak hubuje ze local variable accessed from within inner class
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
                                
                                //TODO ak entita neodkazuje na ziadne metody, len ju zmaz, negeneruj z nej ziadnu triedu.
                                //(neviem sice, kedy by to mohlo nastat, ale spravat by sa to malo takto.)
                                
                                //meno triedy je L_menoSuboruSoSchemouPreEntitu (bez pripony)
                                String className = CLASS_PREFIX + filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                                
                                String classContent = "package " + CLASSES_BASE_PKG + classPackage + ";\n\n";
                                if (! classPackage.equals("")) {
                                    classContent += "import " + CLASSES_BASE_PKG + ".Logger;\n";
                                }
                                classContent += "import java.util.Map;\n\n"
                                              + "public class " + className + " extends Logger {\n";
                                
                                //prejdi vsetky subory so schemickami a generuj metody
                                ArrayNode eventTypes = (ArrayNode)(entitySchemaRoot.get("eventTypes"));
                                for (int i = 0; i < eventTypes.size(); i++) {
                                    JsonNode methodNode = eventTypes.get(i);
                                    Iterator<String> it = methodNode.getFieldNames();
                                    String methodName = "";
                                    String schemaPack = "";
                                    while (it.hasNext()) { //malo by prejst iba raz... TODO vymysliet inak?
                                        methodName = it.next();
                                        schemaPack = methodNode.get(methodName).getTextValue();
                                    }
                                    
                                    //TODO teraz sa pohrabat v tom subore na methodSchemaPath a doplnit vsetko info k metode
                                    String methodSchemaPath = "src" + File.separator + "main" + File.separator + "resources" + File.separator
                                            + schemaPack.replace('.', File.separatorChar) + ".json";
                                    Path methodSchema = FileSystems.getDefault().getPath(methodSchemaPath);
                                    JsonNode methodSchemaRoot = mapper.readTree(methodSchema.toFile());
                                    JsonNode parameters = methodSchemaRoot.get("properties");
                                    Iterator<String> paramsIterator = parameters.getFieldNames();
                                    boolean putComma = false;
                                    classContent += "\n    public static Map<String, Object> " + methodName + "(";
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
                                        switch (param.get("type").getTextValue()) {
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
                                    classContent += ") {\n" + "        return log(new String[]{" + strings + "}" + args + ");\n" + "    }\n";
                                }
                                
                                //potom na zaver dokonci triedu a zapis ju do suboru
                                classContent += "}\n";
                                
                                JavaFileObject file = filer.createSourceFile(CLASSES_BASE_PKG + classPackage + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!
                            
                                file.openWriter()
                                        .append(classContent)
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
}