package cz.muni.fi.pokus.jsonschemaprocessor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
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
    
    private boolean firstRound = true;
    private List<Element> schemasToGenerate = new ArrayList<>();
    private List<String> newClasses = new ArrayList<>();
    
    //TODO zaviest si Stringovske konstanty na nazvy balikov atd, aby sa to tu hore dalo dobre nastavovat
    //TODO vymysliet s tymito... mozno by sa to potom dalo nejak konfigurovat zvonka? ci to je zbytocne, radsej konvencie?
    private static final String SCHEMAS_PACKAGE = "schemas";
    private static final String EVENTS_PACKAGE = "events";

    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
    }

    //multiple rounds cause multiple problems
    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        List<String> processedClasses = new ArrayList<>(); //len mena tried (nie fully qualified), tj predpokladam, ze sa nebudu
                                                           //   opakovat nazvy (TODO alebo to mam nejak osefovat?)
                                                           //edit: ak len 1 pkg EVENTS, nebudu sa opakovat :) ale pozor ak viac
        
        if (env.processingOver()) { //last Round - vygenerujeme schemy zo vsetkych tried okrem tych novych
            for (Element element : schemasToGenerate) {
                String elementName = element.getSimpleName().toString();
                
                if (newClasses.contains(elementName)) {
                    continue;
                }
                
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
                                             + "                    \"type\":\"" + jschType + "\"\n"
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
                    FileObject file = filer.createResource(StandardLocation.SOURCE_OUTPUT, SCHEMAS_PACKAGE, schemaName + ".jsch");
                    file.openWriter()
                            .append(classContentBeginning + classContentRefs + classContentEnd)
                            .close();
                } catch (IOException ex) {
                    //TODO
                }

                //na tomto mieste by som mala mat vygenerovanu komplet schemu pre tuto jednu triedu
            }
        } else { //s najdenymi triedami nic zatial nerob, az na konci; iba si ich zapamataj
            if (firstRound) {
                for (Element element : env.getRootElements()) {
                    String pack = element.getEnclosingElement().toString();

                    if (pack.equals(EVENTS_PACKAGE)) {
                        schemasToGenerate.add(element);
                        processedClasses.add(element.getSimpleName().toString());
                    }
                }
                //firstRound = false --> hlavne to nedavat sem! to sa urobi az po prejdeni vsetkych tych schem
            }
        }
        
        //ok, to by sme mali generovanie schem z tried, a teraz este doplnit zo schem tie triedy, ktore neexistuju
        String path = "src" + File.separatorChar + "main" + File.separatorChar + "java" + File.separatorChar
                    + SCHEMAS_PACKAGE.replace('.', File.separatorChar);

        if (firstRound) {
            //TODO prerobit s pouzitim Java 7 Path API?
            File folder = new File(path);
            File[] files = folder.listFiles();

            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();

                    if (processedClasses.contains(filename.substring(0, filename.length() - 5))) {
                        continue;
                    }

                    if (filename.toLowerCase().endsWith(".jsch")) {
                        //a tu si preskumame ten subor a urobime s nim, co potrebujeme
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root;
                        try {
                            root = mapper.readTree(new File(files[i].toString()));

                            String className = filename.substring(0, 1).toUpperCase()
                                    + filename.substring(1, filename.length() - 5); //meno triedy je meno tej JSON Schemy
                            String classContent =
                                      "package " + EVENTS_PACKAGE + ";\n\n"
                                    + "import java.util.HashMap;\n"
                                    + "import java.util.Map;\n\n"
                                    + "public class " + className + " {\n";

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

                            //a cele to zapisat do suboru
                            JavaFileObject file = filer.createSourceFile(EVENTS_PACKAGE + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!

                            file.openWriter()
                                    .append(classContent)
                                    .close();

                            newClasses.add(className); //poznacit si, ktoru sme vygenerovali teraz
                        } catch (IOException ex) {
                        }

                        //na tomto mieste by som mala mat vygenerovanu triedu so vsetkymi metodami
                    }
                }
            }
            firstRound = false;
        }
        
        return true;
    }
}