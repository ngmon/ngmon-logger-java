package cz.muni.fi.processor;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;

public class MethodInvocationScanner extends TreeScanner<Void, Element> {
    
    private Map<String, Set<String>> entitiesMap = new HashMap<>();
    
    public Map<String, Set<String>> getEntitiesMap() {
        return entitiesMap;
    }
    
    @Override
    public Void visitMethodInvocation(MethodInvocationTree methodTree, Element element) {
        //zistit, ci je to volane na nejakom Namespace (resp na niecom "extends Logger"?). ak hej, a ak je to metoda tag:
        //zisti si poslednu volanu metodu v tomto "riadku" (ked aj blbym parsovanim Stringu). ak to neni tag, zapis to do Mapy
        if (methodTree.getMethodSelect().getKind() == Tree.Kind.MEMBER_SELECT) {
            MemberSelectTree methodSelect = ((MemberSelectTree) methodTree.getMethodSelect());
            String methodName = methodSelect.getIdentifier().toString();
            ExpressionTree object = methodSelect.getExpression();
            
            //zapamataj si tuto metodu (tu poslednu), a hrabkaj sa tam, kym nenarazis na IDENTIFIER alebo MEMBER_SELECT, alebo
            //kym to ne-nebudu tagy.
            while (object.getKind() == Tree.Kind.METHOD_INVOCATION) {
                MethodInvocationTree miTree = (MethodInvocationTree) object;
                if (methodTree.getMethodSelect().getKind() == Tree.Kind.MEMBER_SELECT) {
                    MemberSelectTree methodSel = ((MemberSelectTree) miTree.getMethodSelect());
                    if (! methodSel.getIdentifier().toString().equals("tag") || (miTree.getArguments().size() != 1)) {
                        break;
                    }
                    object = methodSel.getExpression();
                    String entity = miTree.getArguments().get(0).toString();
                    if ((entity.length() >=2) && entity.startsWith("\"") && entity.endsWith("\"")) {
                        entity = entity.substring(1, entity.length()-1);
                    }
                    //pridat do mapy:
                    Set<String> set = entitiesMap.get(entity);
                    if (set != null) {
                        set.add(methodName);
                    } else {
                        set = new HashSet<>();
                        set.add(methodName);
                        entitiesMap.put(entity, set);
                    }
                } else {
                    break;
                }
            }
        }
        
        return null;//ziadne super.visitMethodInvocation(methodTree, element); - potrebujeme prejst cely "riadok" naraz
    }
    
}
