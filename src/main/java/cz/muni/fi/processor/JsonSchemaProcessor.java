package cz.muni.fi.processor;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.annotation.SourceNamespace;
import cz.muni.fi.ast.MethodInvocationInfo;
import cz.muni.fi.ast.MethodInvocationScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JsonSchemaProcessor extends AbstractProcessor {
    
    //TODO vycistit + komentare
    
//    private Filer filer;
    private Messager messager;
    
    private static final String EVENTS_BASE_PKG = "EVENTS";
    
    private long lastBuildTime = 0;
    private boolean firstRound = true;
    private List<Element> schemasToGenerate = new ArrayList<>();
//    private List<String> newClasses = new ArrayList<>();

    private Trees trees;
    private Elements elements;
    
    @Override
    public void init(ProcessingEnvironment env) {
//        filer = env.getFiler();
        messager = env.getMessager();
        
        trees = Trees.instance(env);
        elements = env.getElementUtils();
        
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("src/main/resources/config.properties"));
            lastBuildTime = Long.parseLong(properties.getProperty("lastBuildTime"));
        } catch (IOException ex) {
        }
    }

    @Override
    public boolean process(Set annotations, RoundEnvironment env) {
//        List<String> processedClasses = new ArrayList<>();
                
        if (! env.processingOver()) {
            if (firstRound) {
                List<String> namespaces = new ArrayList<>();
                for (Element element : env.getElementsAnnotatedWith(Namespace.class)) {
                    namespaces.add(element.getSimpleName().toString());
                    String pack = element.getEnclosingElement().toString();
//                    processedClasses.add(pack + "." + element.getSimpleName().toString());
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
                        //TODO opravit to kontrolovanie pretazenych metod nizsie, aby sme to nemuseli prechadzat zbytocne aj tu?
                        Set<String> methods = new HashSet<>();
                        boolean add = true;
                        for (Element elem : element.getEnclosedElements()) {
                            if (elem.getKind() == ElementKind.METHOD) {
                                ExecutableElement method = (ExecutableElement) elem;
                                String methodName = method.getSimpleName().toString();

                                //forbid method overloading
                                if (methods.contains(methodName)) {
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Method overloading not allowed here", elem);
                                    add = false;
                                    break;
                                } else {
                                    methods.add(methodName);
                                }
                            }
                        }

                        if (add) {
                            schemasToGenerate.add(element);
                        }
                    }
                }
                
                for (Element element : env.getRootElements()) { //spracuva vsetky, nie iba @Namespace
//                    processedClasses.add(pack + "." + element.getSimpleName().toString());
                    
                    //process only classes changed since the last build..?
                    //TODO problem: ak sa zmenilo nieco v @SourceNamespace enumoch, treba prekontrolovat vsetko, co to pouziva :/
                    CompilationUnitTree compUnit = trees.getPath(element).getCompilationUnit();
                    List<MethodInvocationInfo> methodsInfo = new ArrayList<>();
                    for (Element e : element.getEnclosedElements()) {
                        Tree methodAST = trees.getTree(e);
                        MethodInvocationScanner scanner = new MethodInvocationScanner();
                        scanner.scan(methodAST, e);
                        methodsInfo.addAll(scanner.getMethodsInvocationInfo());
                    }

                    List<ImportTree> classImports = (List<ImportTree>) compUnit.getImports();
                    String classFQN = getFQN(element);

                    for (MethodInvocationInfo methodInfo : methodsInfo) {
                        if (methodInfo.getObject().endsWith(")") || methodInfo.getArgObject().endsWith(")")) {
                            //to by malo znamenat, ze sa dana metoda volala na vysledku inej metody, nie priamo ako staticka.
                            //kedze nie som kompilator, nemozem to kontrolovat az do takych detailov, aby som spojila entitu s 
                            //  metodou v NS cez volania dalsich metod; kontrolujem to, iba ak je to hned za sebou
                            continue;
                        }

                        String argObjSimpleName;
                        if (methodInfo.getArgObject().lastIndexOf('.') == -1) {
                            argObjSimpleName = methodInfo.getArgObject();
                        } else {
                            argObjSimpleName = methodInfo.getArgObject().substring(methodInfo.getArgObject().lastIndexOf('.') + 1);
                        }
                        if (! namespaces.contains(argObjSimpleName)) {
                            //ak ta druha metoda nie je z nejakeho NS (samozrejme nie je iste, ze ak namespaces.contains(xx), tak 
                            //  to je NS - moze to mat ine FQN. ale ak !contains, urcite to NS nie je, a aspon to trochu osekame.)
                            continue;
                        }

                        //ziskat fqn methodInfo.object a methodInfo.argObject
                        String fqnObject = "";
                        String fqnArgObject = "";
                        List<String> asteriskImports = new ArrayList<>();
                        for (ImportTree imp : classImports) {
                            String importt = imp.getQualifiedIdentifier().toString();

                            if (importt.endsWith(methodInfo.getObject())) {
                                fqnObject = importt;
                            }

                            if (importt.endsWith(methodInfo.getArgObject())) {
                                fqnArgObject = importt;
                            }

                            if (importt.endsWith("*")) {
                                asteriskImports.add(importt);
                            }
                        }

                        if (fqnObject.equals("") || fqnArgObject.equals("")) {
                            if (asteriskImports.isEmpty()) {
                                if (fqnObject.equals("")) {
                                    fqnObject = classFQN.substring(0, classFQN.lastIndexOf('.'))
                                            + "." + methodInfo.getObject();
                                }
                                if (fqnArgObject.equals("")) {
                                    fqnArgObject = classFQN.substring(0, classFQN.lastIndexOf('.'))
                                            + "." + methodInfo.getArgObject();
                                }
                            } else {
                                //TODO najst tie fqn, ak ich este furt nemam (tj. neboli jednoducho zistitelne... -> papier)
                            }
                        }

                        //na tomto mieste uz mame urcite fqn oboch
                        //prejst enum z triedy fqnObject, ktory sa vztahuje na dany fqnArgObject; ci obsahuje tu metodu
                        TypeElement entityClass = elements.getTypeElement(fqnObject);
                        boolean supported = true; //ak tam neni prislusna anotacia, neobmedzujem ziadne metody... TODO moze byt?
                        for (Element elem : entityClass.getEnclosedElements()) {
                            if (elem.getKind() == ElementKind.ENUM) {
                                if (elem.getAnnotation(SourceNamespace.class) != null) {
                                    String sourceNS = elem.getAnnotation(SourceNamespace.class).value();
                                    if (sourceNS.equals(fqnArgObject)) {
                                        boolean found = false;
                                        for (Element el : elem.getEnclosedElements()) {
                                            if (el.toString().equals(methodInfo.getArgMethodName())) {
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (!found) {
                                            //jediny pripad, ked sa moze zmenit supported na false je, ked sme v enume pre dany NS
                                            //  a nie je v nom povolena nasa volana metoda
                                            supported = false;
                                        }
                                    }
                                }
                            }
                        }

                        if (!supported) {
                            compUnit = trees.getPath(methodInfo.getMethodElement()).getCompilationUnit();
                            long start = trees.getSourcePositions().getStartPosition(compUnit, methodInfo.getMethodTree());
                            LineMap lineMap = compUnit.getLineMap();
                            messager.printMessage(Diagnostic.Kind.ERROR, "Method '" + methodInfo.getArgMethodName() + "' not supported by '"
                                    + methodInfo.getObject() + "' (line " + lineMap.getLineNumber(start) + ")", methodInfo.getMethodElement());
                            //TODO hlaska ok?
                        }
                    }
                }
            }
        } else { //last round - generate schemas for all classes (except for those that have just been created)
            for (Element element : schemasToGenerate) {
                String pack = element.getEnclosingElement().toString();
                String schemaName = element.getSimpleName().toString();
                
//                if (newClasses.contains(pack + "." + schemaName)) {
//                    continue;
//                }
                
                try {
                    String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + 
                            EVENTS_BASE_PKG + File.separatorChar + pack.replace('.', File.separatorChar) + File.separatorChar + 
                            schemaName + ".json";
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
                                
                                //forbid method overloading
//                                if (methods.containsKey(methodName)) {
//                                    messager.printMessage(Diagnostic.Kind.ERROR, "Method overloading not allowed here", e); //TODO preco to nespoji s tym Elementom e? :(
//                                    //note: ten subor s json schemou bude sice validny, ale neuplny... asi na to upozornit v tej hlaske
//                                }
                                
                                schema.writeStartObject();
                                schema.writeStringField("$ref", "#/definitions/" + methodName);
                                schema.writeEndObject(); //end {"$ref":"#/definitions/GET"}
                                
                                //get parameter names and types
                                Map<String, String> params = new HashMap<>();
                                for (VariableElement param : method.getParameters()) {
                                    String typeFull = param.asType().toString();
                                    String type = typeFull.substring(typeFull.lastIndexOf(".") + 1);
                                    switch (type) {
                                        case "String":
                                            type = "string";
                                            break;
                                        case "int":
                                            type = "integer";
                                            break;
                                        case "double":
                                        case "float":
                                            type = "number";
                                            break;
                                        case "boolean":
                                            type = "boolean";
                                            break;
                                        default:
                                            type = "object";
                                    }
                                    
                                    params.put(param.getSimpleName().toString(), type);
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
                            //TODO neprechadzat zbytocne znovu, ale poznacit si to uz pri vypisovani properties?
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
            //TODO generuj neexistujuce @Namespace triedy zo schem (...chceme?)
            
            firstRound = false;
        }
        
        return true; //uz moze vratit true, lebo ziadny dalsi processor nie je
    }
    
    private String getFQN(Element classElement) {
        return classElement.getEnclosingElement().toString() + "." + classElement.getSimpleName().toString();
    }
}