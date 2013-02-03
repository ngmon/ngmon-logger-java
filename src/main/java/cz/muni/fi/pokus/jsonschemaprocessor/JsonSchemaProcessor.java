package cz.muni.fi.pokus.jsonschemaprocessor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

/**
 *
 * @author Andrejka
 */
@SupportedAnnotationTypes(value= {"*"})
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO opakujuce sa kusy atd... sprehladnit kod, upratat komentare
    
     /*TODO spisat niekam vsetky konvencie a poziadavky potrebne na spravne fungovanie tohto. pocnuc nielen dependency 
     * na ten projekt s Processorom, ale aj spravnym nastavenim mvn compilera spolu s uvedenim <annotationProcessor>-a!
     * (este skusit pohladat, ci by to neslo inak, lebo takto na to stopercentne niekto zabudne a bude sa divit)
     * 
     * dalej v akych balikoch maju byt tie zdrojove triedy a schemy, aku strukturu maju zhruba mat (co tam MUSI byt, a 
     * ze z tried sa beru len public metody, co moze byt ale vyhoda v tom, ze ako private si tam mozu napchat hocico)
     * 
     * atd atd, proste si dat na tom zalezat, kym si to este pamatam.
     */
    
    private Filer filer;
    private Messager messager;
    
    //TODO zaviest si Stringovske konstanty na nazvy balikov atd, aby sa to tu hore dalo dobre nastavovat
    //TODO vymysliet s tymito... mozno by sa to potom dalo nejak konfigurovat zvonka? ci to je zbytocne, radsej konvencie?
    private static final String SCHEMAS_PACKAGE = "schemas";
    private static final String EVENTS_PACKAGE = "events";

    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        List<String> processedClasses = new ArrayList<>(); //len mena tried (nie fully qualified), tj predpokladam, ze sa nebudu
                                                           //   opakovat nazvy (TODO alebo to mam nejak osefovat?)
        Set<String> schemasPackages = new HashSet<>();
        
        for (Element element : env.getRootElements()) {
            String pack = element.getEnclosingElement().toString();
            String elementName = element.getSimpleName().toString();
            
            if (! (pack.equals(EVENTS_PACKAGE) || pack.endsWith("." + EVENTS_PACKAGE))) {
                if (pack.equals(SCHEMAS_PACKAGE) || pack.endsWith("." + SCHEMAS_PACKAGE)) {
                    schemasPackages.add(pack); //to be processed later
                }
                continue;
            }
            
            if (processedClasses.contains(elementName)) {
                continue;
            } else {
                processedClasses.add(elementName);
            }
            
            //a tu si preskumame ten subor a urobime s nim, co potrebujeme
            String schemaName = elementName.substring(0, 1).toUpperCase() + elementName.substring(1);
            String classContentBeginning = "{\n"
                                         + "    \"$schema\": \"http://json-schema.org/schema#\",\n"
                                         + "    \"title\":\"" + schemaName + "\",\n"
                                         + "    \"type\": [\"object\"],\n"
                                         + "    \"properties\": {\n"
                                         + "        \"eventType\": {\n" //mozem to nazvat aj inak ako eventType, ak to neni OK
                                         + "            \"type\":\"object\",\n"
                                         + "            \"oneOf\": [\n";
            
            String classContentRefs = ""; //sem potom nasupem tie referencie na subschemy metod
            
            String classContentEnd = "\n"
                                   + "            ]\n"
                                   + "        }\n"
                                   + "    },\n"
                                   + "    \"required\":[\"eventType\"],\n"
                                   + "    \"definitions\": {\n";
            boolean putComma = false;
            for (Element e: element.getEnclosedElements()) {
                if ((e.getKind() == ElementKind.METHOD) && e.getModifiers().contains(Modifier.PUBLIC)) {
                    //najprv to doplnit do referencii
                    if (putComma) {
                        classContentRefs += ",\n";
                        classContentEnd += ",\n";
                    } else {
                        putComma = true;
                    }
                    classContentRefs += "                {\"$ref\":\"#/definitions/" + e.getSimpleName().toString() + "\"}";
                    
                    //potom napisat subschemu pre danu metodu
                    classContentEnd += "        \"" + e.getSimpleName().toString() + "\": {\n"
                                     + "            \"properties\":{\n";
                    //vypisat vsetky parametre aj s typmi
                    ExecutableElement method = (ExecutableElement) e;
                    boolean comma = false;
                    List<String> paramNames = new ArrayList<>();
                    for (VariableElement param : method.getParameters()) {
                        String typeFull = param.asType().toString();
                        String type = typeFull.substring(typeFull.lastIndexOf(".") + 1); //kvoli fully qualified menam
                        if (comma) {
                            classContentEnd += ",\n";
                        } else {
                            comma = true;
                        }
                        classContentEnd += "                \"" + param.getSimpleName().toString() + "\": {\n"
                                         + "                    \"type\":\"" + type + "\"\n"
                                         + "                }";
                        paramNames.add(param.getSimpleName().toString());
                    }
                    
                    classContentEnd += "\n"
                                     + "            },\n"
                                     + "            \"required\":[";
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
                            + "            \"additionalProperties\":false\n"
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
                Path file = Paths.get("src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar
                        /*+ SCHEMAS_PACKAGE.replace('.', File.separatorChar) + File.separatorChar*/ + schemaName + ".json"); //TODO .jsch miesto .json
                //z nejakeho dovodu mu vadi platna schema v src/main/java; neplatna (tj napr bez jednej zatvorky na konci)
                //  mu nevadi, ani platna v resources.
                //TODO vyriesit, alebo prehodnotit celu tu vec s vyhladavanim schem v balikoch konciacich na SCHEMAS!
                
                Files.deleteIfExists(file);
                file = Files.createFile(file);
                try (BufferedWriter writer = Files.newBufferedWriter(file, Charset.forName("UTF-8"), new OpenOption[] {StandardOpenOption.WRITE})) {
                    writer.write(classContentBeginning + classContentRefs + classContentEnd);
                    writer.flush();
                }
            } catch (IOException ex) {
                //TODO
            }

            //na tomto mieste by som mala mat vygenerovanu komplet schemu pre tuto jednu triedu
            
            
        }
        
        //ok, to by sme mali generovanie schem z tried, a teraz este doplnit zo schem tie triedy, ktore neexistuju
        for (String pack : schemasPackages) {
            String path = "src" + File.separatorChar + "main" + File.separatorChar + "java" + File.separatorChar
                        + pack.replace('.', File.separatorChar);

            
            //TODO prerobit s pouzitim java.nio z Javy 7?
            //------------------------------------
//            try (DirectoryStream<Path> dir = Files.newDirectoryStream(FileSystems.getDefault().getPath(path), "*.json")) { //TODO potom zmenit na .JSCH
//                for (Path file : dir) { // Iterate over the paths in the directory
//                    try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
//                        String lineFromFile;
//                        System.out.println("The contents of file are: ");
//                        while ((lineFromFile = reader.readLine()) != null) {
//                            System.out.println(lineFromFile);
//                        }
//                    } catch (IOException exception) {
//                    }
//                    
//                }
//            } catch (IOException e) {
//            }
            //------------------------------------
            
            
            File folder = new File(path);
            File[] files = folder.listFiles();

            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();
                    
                    if (processedClasses.contains(filename)) {
                        continue;
                    } else {
                        processedClasses.add(filename);
                    }
                    
                    //TODO prerobit celu tuto cast podla aktualnej struktury tej schemy
                    if (filename.toLowerCase().endsWith(".json")) {//TODO vymenit za .jsch
                        //a tu si preskumame ten subor a urobime s nim, co potrebujeme
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode entity;
                        try {
                            entity = mapper.readTree(new File(files[i].toString()));

                            String className = filename.substring(0, 1).toUpperCase()
                                    + filename.substring(1, filename.length() - 5); //meno triedy je meno tej JSON Schemy
                            String classContent =
                                      "package " + EVENTS_PACKAGE + ";\n\n"
                                    + "import java.util.HashMap;\n"
                                    + "import java.util.Map;\n\n"
                                    + "public class " + className + " {\n";

                            List<String> keys = new ArrayList<>();
                            //TODO vymysliet nejak inak, toto je celkom risk, zacinat metodu, ked neviem, ci budu 
                            //  dake parametre v tej scheme
                            classContent += "\n    public static Map<String, Object> " + entity.get("eventName").getTextValue().toLowerCase() + "(";
                            Iterator<String> iterator = entity.getFieldNames();
                            boolean putComma = false; //TODO nejak inak? :)

                            //TODO potom pozor na to prechadzanie toho stromu - json schema tam nebude mat rovno hodnoty, ale 
                            //dalsi objekt, a v nom atributy napr. "type":"number" (alebo nieco podobne)
                            while (iterator.hasNext()) {
                                String attributeName = iterator.next();
                                if (!attributeName.equals("eventName")) { //zatial, kvoli tomu podvodu s jedinou metodou v scheme
                                    if (putComma) {
                                        classContent += ", ";
                                    } else {
                                        putComma = true;
                                    }

                                    keys.add(attributeName);

                                    //TODO zaviest si private pomocne metody na vypis niecoho uceleneho, napriklad 
                                    //  zapisPublicMetoduSReturnMap(String nazovMetody), atd. proste aby sa to tu sprehladnilo

                                    //predpokladam, ze tam nemozu byt polia ani objekty, iba jednoduche hodnoty. ci?
                                    //TODO Object, ak to je pole alebo objekt
                                    JsonNode attribute = entity.get(attributeName);
                                    if (attribute.isTextual()) {
                                        classContent += "String ";
                                    } else {
                                        if (attribute.isInt()) {
                                            classContent += "int ";
                                        } else {
                                            if (attribute.isNumber()) {
                                                classContent += "double ";
                                            } else {
                                                if (attribute.isBoolean()) {
                                                    classContent += "boolean ";
                                                } else {
                                                    classContent += "Object ";
                                                }
                                            }
                                        }
                                    }
                                    classContent += attributeName;
                                }
                            }

                            //zavriet tu zatvorku za parametrami metody, dopisat telo metody
                            classContent += ") {\n"
                                    + "        return log(new String[]{";
                            //tu musia nasledovat vsetky parametre oddelene ciarkami, pozor, v uvodzovkach! \"
                            putComma = false; //TODO krajsie? :)
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
                            classContent += ");\n"  //koniec volania logovacej metody
                                    + "    }\n"; //uzavriet metodu

                            //TODO viac metod!


                            //posledna metoda
                            //spolocne veci pre vsetky metody, aby boli malinke
                            classContent += "\n    private static Map<String, Object> log(String[] names, Object... values) {\n"
                                            + "        Map<String, Object> map = new HashMap<String, Object>();\n\n"
                                            + "        for (int i = 0; i < names.length; i++) {\n"
                                            + "            map.put(names[i], values[i]);\n"
                                            + "        }\n\n"
                                            + "        return map;\n"
                                            + "    }\n";

                            classContent += "}\n"; //uzavriet triedu
                            System.out.println(classContent);
                            //a cele to zapisat do suboru
                            //In general, processors must not knowingly attempt to overwrite existing files that 
                            //   were not generated by some processor. --> Skoda, presne to som chcela urobit!
                            JavaFileObject file = filer.createSourceFile(EVENTS_PACKAGE + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!

                            file.openWriter()
                                    .append(classContent)
                                    .close();
                        } catch (IOException ex) {
                        }

                        //na tomto mieste by som mala mat vygenerovanu triedu so vsetkymi metodami
                    }
                }
            }
        }
        
        return true;
    }
}