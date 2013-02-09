package cz.muni.fi.processor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 *
 * @author Andrejka
 */
@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO zmenit kompletne strukturu schem - zvlast entity, zvlast typy udalosti
    /* pozor na tie baliky teraz:
       triede src/main/java/LOGGER.bla.bla.L_Entity.java zodpoveda
       schema src/main/resources/ENTITIES.bla.bla.Entity.json,
       v ktorej su vymenovane potrebne malinke schemy z src/main/resources/SCHEMAS.(hocico) */
    
    //TODO normalne komentare
    
    private Filer filer;
    private Messager messager; //TODO warningy pri chybach? (neocakavana struktura zdrojakov atd.)
    
    private static final String SCH_ENTITIES_BASE_PKG = "ENTITIES";
    private static final String SCH_EVENTTYPES_BASE_PKG = "SCHEMAS";
    private static final String CLASSES_BASE_PKG = "LOGGER";
    private static final String CLASS_PREFIX = "L_";
    
    private long lastBuildTime = 0;
    private boolean firstRound = true; //TODO nejak lepsie by to neslo? :)
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

    //multiple rounds cause multiple problems
    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        List<String> processedClasses = new ArrayList<>(); //TODO davat sem fully qualified mena, resp. s balikmi okrem LOGGER
        
        if (! env.processingOver()) { //s najdenymi triedami nic zatial nerob, az na konci; iba si ich zapamataj
            if (firstRound) {
                for (Element element : env.getRootElements()) {
                    String pack = element.getEnclosingElement().toString();
                    
                    if (pack.equals(CLASSES_BASE_PKG) || pack.startsWith(CLASSES_BASE_PKG + ".")) { //TODO
                        processedClasses.add(element.getSimpleName().toString());
                        
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
                //firstRound = false --> hlavne to nedavat sem! je to dolu az po prejdeni vsetkych tych schem
            }
        } else { //last Round - vygenerujeme schemy zo vsetkych tried okrem tych novych
            for (Element element : schemasToGenerate) {
                String elementName = element.getSimpleName().toString();
                
                if (newClasses.contains(elementName)) {
                    continue;
                }
                
                String schemaName = elementName.substring(CLASS_PREFIX.length()); //oddelat prefix z nazvu triedy
                String classContentBeginning = "{\n"
                                             + "    \"$schema\": \"http://json-schema.org/schema#\",\n"
                                             + "    \"title\": \"" + schemaName + "\",\n"
                                             + "    \"type\": [\"object\"],\n"
                                             + "    \"properties\": {\n"
                                             + "        \"eventType\": {\n" //TODO nazvat inak ako eventType?
                                             + "            \"type\": \"object\",\n"
                                             + "            \"oneOf\": [\n";

                String classContentRefs = ""; //sem potom nasupem tie odkazy na subschemy metod

                String classContentEnd = "\n"
                                       + "            ]\n"
                                       + "        }\n"
                                       + "    },\n"
                                       + "    \"required\": [\"eventType\"],\n"
                                       + "    \"definitions\": {\n";
                boolean putComma = false;
                for (Element e: element.getEnclosedElements()) {
                    if ((e.getKind() == ElementKind.METHOD) && e.getModifiers().contains(Modifier.PUBLIC)) {
                        ExecutableElement method = (ExecutableElement) e;
                        
                        //skontrolovat, ci vracia Map<String,Object> - netreba, budu void (budu len logovat) a do jsch idu VSETKY
                        
                        //najprv to doplnit do odkazov
                        if (putComma) {
                            classContentRefs += ",\n";
                            classContentEnd += ",\n";
                        } else {
                            putComma = true;
                        }
                        classContentRefs += "                {\"$ref\": \"#/definitions/" + e.getSimpleName().toString() + "\"}";

                        //potom napisat subschemu pre danu metodu
                        classContentEnd += "        \"" + e.getSimpleName().toString() + "\": {\n"
                                         + "            \"properties\": {\n";
                        //vypisat vsetky parametre aj s typmi
                        boolean comma = false;
                        List<String> paramNames = new ArrayList<>();
                        for (VariableElement param : method.getParameters()) {
                            String typeFull = param.asType().toString();
                            String type = typeFull.substring(typeFull.lastIndexOf(".") + 1); //kvoli fully qualified menam
                            String jschType;
                            switch (type) {
                                case "String":
                                    jschType = "string";
                                    break;
                                case "int":
                                    jschType = "integer";
                                    break;
                                case "double":
                                case "float":
                                    jschType = "number";
                                    break;
                                case "boolean":
                                    jschType = "boolean";
                                    break;
                                default:
                                    jschType = "object";
                            }

                            if (comma) {
                                classContentEnd += ",\n";
                            } else {
                                comma = true;
                            }
                            classContentEnd += "                \"" + param.getSimpleName().toString() + "\": {\n"
                                             + "                    \"type\": \"" + jschType + "\"\n"
                                             + "                }";
                            paramNames.add(param.getSimpleName().toString());
                        }

                        classContentEnd += "\n"
                                         + "            },\n"
                                         + "            \"required\": [";
                        comma = false;
                        for (String param : paramNames) {
                            if (comma) {
                                classContentEnd += ",";
                            } else {
                                comma = true;
                            }
                            classContentEnd += "\"" + param + "\"";
                        }
                        classContentEnd += "],\n"
                                + "            \"additionalProperties\": false\n"
                                + "        }";
                    }
                }

                classContentEnd += "\n"
                                 + "    }\n"
                                 + "}";

                //...a cele to zapisat do suboru
                //In general, processors must not knowingly attempt to overwrite existing files that
                //   were not generated by some processor. --> Skoda, presne to idem totiz urobit.
                try {
                    //TODO este doriesit tie baliky
                    //filer.createResource som nedokazala presvedcit, aby tie subory vytvaral v src/main/resources, takze rucne:
                    String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + SCH_EVENTTYPES_BASE_PKG + File.separatorChar + schemaName + ".json";
                    Path file = FileSystems.getDefault().getPath(path);
                    try (BufferedWriter writer = Files.newBufferedWriter(file, Charset.defaultCharset())) {
                        writer.append(classContentBeginning + classContentRefs + classContentEnd);
                    }
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + schemaName + ".json");
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
        
        //ok, teraz este doplnit zo schem tie triedy, ktore neexistuju
        String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar
                    + SCH_EVENTTYPES_BASE_PKG;

        if (firstRound) {
            //TODO prerobit s pouzitim Java 7 Path API?
            File folder = new File(path);
            File[] files = folder.listFiles();

            //TODO preliezt rekurzivne vsetky baliky
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();

                    if (processedClasses.contains(CLASS_PREFIX + filename.substring(0, filename.length() - 5))) {
                        continue;
                    }

                    if (filename.toLowerCase().endsWith(".json")) {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root;
                        try {
                            root = mapper.readTree(new File(files[i].toString()));

                            //meno triedy je meno .jsch suboru (bez pripony)
                            String className = CLASS_PREFIX + filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                            
                            String classContent = "package " + CLASSES_BASE_PKG + ";\n\n" //TODO doplnit package tej triedy
                                                + "import " + CLASSES_BASE_PKG + ".Logger;\n"
                                                + "import java.util.Map;\n\n"
                                                + "public class " + className + " extends Logger {\n";

                            JsonNode definitions = root.get("definitions");
                            Iterator<String> methodsIterator = definitions.getFieldNames();
                            while (methodsIterator.hasNext()) {
                                List<String> keys = new ArrayList<>();
                                String methodName = methodsIterator.next();
                                classContent += "\n    public static Map<String, Object> " + methodName + "(";
                                JsonNode method = definitions.get(methodName);
                                JsonNode parameters = method.get("properties");
                                Iterator<String> paramsIterator = parameters.getFieldNames();
                                boolean putComma = false;
                                while (paramsIterator.hasNext()) {
                                    if (putComma) {
                                        classContent += ", ";
                                    } else {
                                        putComma = true;
                                    }
                                    String paramName = paramsIterator.next();
                                    keys.add(paramName);
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
                                }
                                //zavriet tu zatvorku za parametrami metody, dopisat telo metody
                                classContent += ") {\n"
                                        + "        return log(new String[]{";
                                //tu musia nasledovat vsetky parametre oddelene ciarkami, pozor, v uvodzovkach!
                                putComma = false;
                                for (int k = 0; k < keys.size(); k++) {
                                    if (putComma) {
                                        classContent += ",";
                                    } else {
                                        putComma = true;
                                    }

                                    classContent += "\"" + keys.get(k) + "\"";
                                }
                                //a tu skonci to pole stringov
                                classContent += "}";
                                //a odtialto tam zas idu vsetky parametre
                                for (int k = 0; k < keys.size(); k++) {
                                    classContent += ", " + keys.get(k);
                                }
                                classContent += ");\n" //koniec volania logovacej metody
                                        + "    }\n"; //uzavriet metodu
                            }

                            //TODO presunut do Loggera
//                            //posledna metoda
//                            //spolocne veci pre vsetky metody, aby boli malinke
//                            classContent += "\n    private static Map<String, Object> log(String[] names, Object... values) {\n"
//                                            + "        Map<String, Object> map = new HashMap<>();\n\n"
//                                            + "        for (int i = 0; i < names.length; i++) {\n"
//                                            + "            map.put(names[i], values[i]);\n"
//                                            + "        }\n\n"
//                                            + "        return map;\n"
//                                            + "    }\n";

                            classContent += "}\n"; //uzavriet triedu

                            //a cele to zapisat do suboru
                            JavaFileObject file = filer.createSourceFile(CLASSES_BASE_PKG + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!

                            file.openWriter()
                                    .append(classContent)
                                    .close();

                            newClasses.add(className); //poznacit si, ktoru sme vygenerovali teraz
                        } catch (IOException ex) {
                            messager.printMessage(Diagnostic.Kind.ERROR, filename + ": unexpected format of schema");
                        }
                    }
                }
            }
            firstRound = false;
        }
        
        return true;
    }
}