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
    
    private Map<String, Set<String>> tagsMap = new HashMap<>();
    
    public Map<String, Set<String>> getTagsMap() {
        return tagsMap;
    }
    
    @Override
    public Void visitMethodInvocation(MethodInvocationTree methodTree, Element element) {
        if (methodTree.getMethodSelect().getKind() == Tree.Kind.MEMBER_SELECT) {
            MemberSelectTree methodSelect = ((MemberSelectTree) methodTree.getMethodSelect());
            String methodName = methodSelect.getIdentifier().toString();
            ExpressionTree object = methodSelect.getExpression();
            
            while (object.getKind() == Tree.Kind.METHOD_INVOCATION) {
                MethodInvocationTree miTree = (MethodInvocationTree) object;
                if (methodTree.getMethodSelect().getKind() == Tree.Kind.MEMBER_SELECT) {
                    MemberSelectTree methodSel = ((MemberSelectTree) miTree.getMethodSelect());
                    if (! methodSel.getIdentifier().toString().equals("tag") || (miTree.getArguments().size() != 1)) {
                        break;
                    }
                    object = methodSel.getExpression();
                    String tag = miTree.getArguments().get(0).toString();
                    if ((tag.length() >=2) && tag.startsWith("\"") && tag.endsWith("\"")) {
                        tag = tag.substring(1, tag.length()-1);
                    }
                    
                    Set<String> set = tagsMap.get(tag);
                    if (set != null) {
                        set.add(methodName);
                    } else {
                        set = new HashSet<>();
                        set.add(methodName);
                        tagsMap.put(tag, set);
                    }
                } else {
                    break;
                }
            }
        }
        
        return null;
    }
    
}
