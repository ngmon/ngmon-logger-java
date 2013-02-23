package cz.muni.fi.ast;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

public class MethodChecker extends TreeScanner<Void, Element> { //Void = return val; Element = element, ktoreho Tree skenujeme
    
    private Messager messager;
    private Trees trees;
    private Elements elements;
    
    public MethodChecker(Messager messager, Elements elements, Trees trees) {
        super();
        this.messager = messager;
        this.elements = elements;
        this.trees = trees;
    }
    
    @Override
    public Void visitMethodInvocation(MethodInvocationTree methodTree, Element element) {
        //TODO nieco lepsie ako parsovanie toho stringu?
        
        //methodTree.toString() vracia bud "super()", alebo cele volanie metody, tj L_EntityXY.log(ApacheEvents.event1("abc", 1))
        //(alebo ak by tam bolo este daco za tym, tak az po strednik)
        //methodTree.getMethodSelect().toString() vracia "super" alebo "L_EntityXY.log"
        if (! methodTree.toString().equals("super()")) {
            try {
                //TODO tieto substringove cary zhavaruju, ak tam niekto bachne fqn miesto importu... doriesit
                String arg = ((MethodInvocationTree) (methodTree.getArguments().get(0))).toString(); //ApacheEvents.event1("abc",1)
                String checkInEnum = arg.substring(arg.indexOf('.') + 1, arg.indexOf('(')); //TODO fqn osetrit
                String entityClassName = (methodTree.toString()).substring(0, methodTree.toString().indexOf('.'));
                
                //toto by bolo dokonale, ale vraj to nema tu metodu (aj ked v dokumentacii je, wtf)
                //trees.printMessage(Diagnostic.Kind.WARNING, "blablabla", methodTree, compUnit);
                
                //zatial natvrdo, na vyskusanie. TODO dokoncit
                TypeElement entityClass = elements.getTypeElement("cz.muni.fi.loggen2." + entityClassName);
                boolean found = false;
                for (Element elem : entityClass.getEnclosedElements()) {//cez vsetky metody, atributy a enumy danej triedy
                    if (elem.getKind() == ElementKind.ENUM) {
                        //TODO dodatocne kontroly, ci je to ten spravny enum pre tento typ metody (podla @SourceNamespace a 
                        // triedy, na ktorej sa volala tato metodka (rovnako zistit fqn ako vyssie))
                        //ak tam nebude anotacia, bachnut balik tejto triedy
                        
                        //cez vsetky tie hodnoty toho enumu plus implicit contructor plus implicit values and valueOf methods
                        for (Element e : elem.getEnclosedElements()) {
                            if (e.toString().equals(checkInEnum)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
                if (!found) {
                    CompilationUnitTree compUnit = trees.getPath(element).getCompilationUnit();
                    long start = trees.getSourcePositions().getStartPosition(compUnit, methodTree);
                    LineMap lineMap = compUnit.getLineMap();
                    messager.printMessage(Diagnostic.Kind.ERROR, "Method '" + checkInEnum + "' not supported by '" 
                            + entityClassName + "' (line " + lineMap.getLineNumber(start) + ")", element);
                }
                
            } catch (ClassCastException e) {
                //TODO
            }
        }
        
        return null; // = nezostupuj na potomkov
    }
}
