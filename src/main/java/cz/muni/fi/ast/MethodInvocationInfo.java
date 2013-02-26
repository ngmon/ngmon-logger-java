package cz.muni.fi.ast;

import com.sun.source.tree.MethodInvocationTree;
import javax.lang.model.element.Element;

public class MethodInvocationInfo {
    
    private String object;
    private String methodName;
    private String argObject;
    private String argMethodName;
    private Element methodElement;
    private MethodInvocationTree methodTree;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getArgObject() {
        return argObject;
    }

    public void setArgObject(String argObject) {
        this.argObject = argObject;
    }

    public String getArgMethodName() {
        return argMethodName;
    }

    public void setArgMethodName(String argMethodName) {
        this.argMethodName = argMethodName;
    }
    
    public Element getMethodElement() {
        return methodElement;
    }

    public void setMethodElement(Element methodElement) {
        this.methodElement = methodElement;
    }
    
    public MethodInvocationTree getMethodTree() {
        return methodTree;
    }

    public void setMethodTree(MethodInvocationTree methodTree) {
        this.methodTree = methodTree;
    }
    
    //TODO prec (potrebne len pre debug)
    @Override
    public String toString() {
        return object + "." + methodName + "(" + argObject + "." + argMethodName + "())";
    }
}
