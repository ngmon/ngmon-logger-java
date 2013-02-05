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
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
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
    
    //TODO normalne komentare
    
    private Filer filer;
    private Messager messager; //TODO warningy pri chybach? (neocakavana struktura zdrojakov atd.)
    
    private static final String SCHEMAS_PACKAGE = "schemas";
    private static final String EVENTS_PACKAGE = "events";
    
    private boolean firstRound = true; //TODO nejak lepsie by to neslo? :)
    private List<Element> schemasToGenerate = new ArrayList<>();
    private List<String> newClasses = new ArrayList<>();

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
        
        if (! env.processingOver()) { //s najdenymi triedami nic zatial nerob, az na konci; iba si ich zapamataj
            if (firstRound) {
                for (Element element : env.getRootElements()) {
                    String pack = element.getEnclosingElement().toString();

                    if (pack.equals(EVENTS_PACKAGE)) {
                        schemasToGenerate.add(element);
                        processedClasses.add(element.getSimpleName().toString());
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
                
                String schemaName = elementName.substring(0, 1).toUpperCase() + elementName.substring(1);
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
                        
                        //skontrolovat, ci vracia Map<String,Object>
                        if (method.getReturnType() instanceof DeclaredType) {
                            DeclaredType returnType = (DeclaredType)(method.getReturnType());
                            if (! returnType.toString().equals("java.util.Map<java.lang.String,java.lang.Object>")) {
                                messager.printMessage(Diagnostic.Kind.NOTE, "Method \"" + method.getSimpleName().toString() + "\" will not be reflected in the corresponding JSON schema. Only public methods returning Map<String,Object> are included.", method);
                                continue;
                            }
                        } else {
                            messager.printMessage(Diagnostic.Kind.NOTE, "Method \"" + method.getSimpleName().toString() + "\" will not be reflected in the corresponding JSON schema. Only public methods returning Map<String,Object> are included.", method);
                            continue;
                        }
                        
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
                    FileObject file = filer.createResource(StandardLocation.SOURCE_OUTPUT, SCHEMAS_PACKAGE, schemaName + ".jsch");
                    file.openWriter()
                            .append(classContentBeginning + classContentRefs + classContentEnd)
                            .close();
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Error writing file " + schemaName + ".jsch");
                }
            }
        }
        
        //ok, teraz este doplnit zo schem tie triedy, ktore neexistuju
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
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root;
                        try {
                            root = mapper.readTree(new File(files[i].toString()));

                            //meno triedy je meno .jsch suboru (bez pripony)
                            String className = filename.substring(0, 1).toUpperCase() + filename.substring(1, filename.length() - 5);
                            
                            String classContent = "package " + EVENTS_PACKAGE + ";\n\n"
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
                                            + "        Map<String, Object> map = new HashMap<>();\n\n"
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