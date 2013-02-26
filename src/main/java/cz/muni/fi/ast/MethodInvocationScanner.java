package cz.muni.fi.ast;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;

public class MethodInvocationScanner extends TreeScanner<Void, Element> {
    
    List<MethodInvocationInfo> methodsInfo = new ArrayList<>();
    
    public List<MethodInvocationInfo> getMethodsInvocationInfo() {
        return this.methodsInfo;
    }
    
    @Override
    public Void visitMethodInvocation(MethodInvocationTree methodTree, Element element) {
        MethodInvocationInfo methodInfo = new MethodInvocationInfo();
        ExpressionTree methodSelect = methodTree.getMethodSelect();
        if (methodSelect.getKind() == Tree.Kind.MEMBER_SELECT) {
            String methodName = ((MemberSelectTree) methodSelect).getIdentifier().toString();
            String object = ((MemberSelectTree) methodSelect).getExpression().toString();
            if (methodTree.getArguments().size() == 1) {
                ExpressionTree arg = methodTree.getArguments().get(0);
                if (arg.getKind() == Tree.Kind.METHOD_INVOCATION) {
                    MethodInvocationTree argMethodTree = (MethodInvocationTree) arg;
                    ExpressionTree argMethodSelect = argMethodTree.getMethodSelect();
                    if (argMethodSelect.getKind() == Tree.Kind.MEMBER_SELECT) {
                        methodInfo.setMethodName(methodName);
                        methodInfo.setObject(object);
                        String argMethodName = ((MemberSelectTree) argMethodSelect).getIdentifier().toString();
                        String argObject = ((MemberSelectTree) argMethodSelect).getExpression().toString();
                        methodInfo.setArgMethodName(argMethodName);
                        methodInfo.setArgObject(argObject);
                        methodInfo.setMethodElement(element);
                        methodInfo.setMethodTree(methodTree);
                        methodsInfo.add(methodInfo);
                    }
                } //inak nas to nezaujima zatial
            }
        } //else volanie lokalnej metody, nezaujima ma
        
        return super.visitMethodInvocation(methodTree, element); //zostup aj na uzly potomkov
    }
}
