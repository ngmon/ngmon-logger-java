package cz.muni.fi.processor;

import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import cz.muni.fi.ast.MethodChecker;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes({"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class AcceptableMethodsProcessor extends AbstractProcessor {
    
    private Messager messager;
    
    private Trees trees;
    private Elements elements;
    
    private long lastBuildTime = 0;

    @Override
    public void init(ProcessingEnvironment env) {
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
    public boolean process(Set elements, RoundEnvironment env) {
        for (Element element : env.getRootElements()) {
            String pack = element.getEnclosingElement().toString();
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
                for (Element e : element.getEnclosedElements()) {
                    if ((e.getKind() == ElementKind.METHOD) && e.getModifiers().contains(Modifier.PUBLIC)) {
                        ExecutableElement method = (ExecutableElement) e;
                        //ziskat AST pre danu triedu:
                        Tree classAST = trees.getTree(method);
                        MethodChecker checker = new MethodChecker(messager, this.elements, trees);
                        //preskenovat to, a uz zaroven aj vypisovat errory (dostalo to messager)
                        checker.scan(classAST, method);
                    }
                }
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
        
        return true;
    }
}