package cz.muni.fi.processor;

import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class TagsProcessor extends AbstractProcessor {
    
    private Trees trees;
    private boolean firstRound = true;
    
    @Override
    public void init(ProcessingEnvironment env) {
        trees = Trees.instance(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        if (firstRound) {
            Map<String, Set<String>> tagsMap = new HashMap<>();
            for (Element element : env.getRootElements()) {
                Tree methodAST = trees.getTree(element);
                MethodInvocationScanner scanner = new MethodInvocationScanner();
                scanner.scan(methodAST, element);
                Map<String, Set<String>> map = scanner.getTagsMap();

                for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                    Set<String> set = tagsMap.get(entry.getKey());
                    if (set != null) {
                        set.addAll(entry.getValue());
                    } else {
                        set = new HashSet<>();
                        set.addAll(entry.getValue());
                        tagsMap.put(entry.getKey(), set);
                    }
                }
            }
            
            //TODO format?
            String path = "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar + "tags-eventtypes.txt";
            Path file = FileSystems.getDefault().getPath(path);
            try {
                Files.createDirectories(file.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(file, Charset.defaultCharset())) {
                    for (Map.Entry<String, Set<String>> entry : tagsMap.entrySet()) {
                        writer.append(entry.getKey()).append('=');
                        for (String val : entry.getValue()) {
                            writer.append(val).append(',');
                        }
                        writer.newLine();
                    }
                    writer.flush();
                }
            } catch (IOException ex) {
                Logger.getLogger(TagsProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            firstRound = false;
        }
        
        return true;
    }
    
}
