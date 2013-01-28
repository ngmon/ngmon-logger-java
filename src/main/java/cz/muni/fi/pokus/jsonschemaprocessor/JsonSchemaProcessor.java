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
import javax.tools.JavaFileObject;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

/**
 *
 * @author Andrejka
 */
@SupportedAnnotationTypes(value= {"*"})
public class JsonSchemaProcessor extends AbstractProcessor { 

    private Filer filer;
    private Messager messager;

    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public boolean process(Set elements, RoundEnvironment env) {
        //tu si budem schovavat *schemas baliky, v ktorych som uz spracovala vsetky .jsch
        List<String> packages = new ArrayList<>(); //TODO vymysliet potom inak
        
        for (Element element : env.getRootElements()) {
            //hack:  element.getEnclosingElement().toString()  vypise package danej triedy. este otestovat, ci v tom nie je 
            //   daky hacik a niekedy by to nevypisalo nieco ine, ci sa na to mozeme spolahnut
            //TODO:  lepsie povedane, skontrolovat, ci tieto elementy mozu byt len triedy, lebo to zatial vyzera, ze ano
            
            //chceme len triedy v baliku (hocico).schemas
            String schemasPackage = element.getEnclosingElement().toString();
            if (! (schemasPackage.endsWith(".schemas") || (schemasPackage.equals("schemas")))) {
                continue;
            }
            
            if (packages.contains(schemasPackage)) {
                continue; //ten sme uz spracovali
            } else {
                packages.add(schemasPackage);
            }
            
            String path = "src" + File.separatorChar + "main" + File.separatorChar + "java" + File.separatorChar
                    + schemasPackage.replace('.', File.separatorChar);

            File folder = new File(path);
            File[] files = folder.listFiles();

            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();
                    if (filename.toLowerCase().endsWith(".json")) { //TODO vymenit za .jsch
                        //a tu si preskumame ten subor a urobime s nim, co potrebujeme
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode entity;
                        try {
                            entity = mapper.readTree(new File(files[i].toString()));

                            String pckg = "events";
                            String className = filename.substring(0, 1).toUpperCase()
                                    + filename.substring(1, filename.length() - 5); //meno triedy je meno tej JSON Schemy
                            String classContent =
                                      "package " + pckg + ";\n\n"
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
                                                classContent += "String ";
                                            }
                                        }
                                    }
                                    classContent += attributeName;
                                }
                            }

                            //zavriet tu zatvorku za parametrami metody, dopisat telo metody
                            classContent += "){\n"
                                    + "        Map<String, Object> map = new HashMap<String, Object>();\n\n";

                            for (int k = 0; k < keys.size(); k++) {
                                classContent += "        map.put(\"" + keys.get(k) + "\", " + keys.get(k) + ");\n";
                            }

                            classContent += "\n" + "        return map;\n";
                            classContent += "    }\n\n"; //uzavriet metodu


                            classContent += "}\n"; //uzavriet triedu
                            System.out.println(classContent);
                            //a cele to zapisat do suboru
                            JavaFileObject file = filer.createSourceFile(
                                    pckg + "." + className); //bacha! povodne tu bolo "/" miesto ".", a preto to neslo!

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