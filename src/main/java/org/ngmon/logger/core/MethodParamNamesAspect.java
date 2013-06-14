package org.ngmon.logger.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class MethodParamNamesAspect {
    
    /**
     * Intercepts execution of methods declared within @Namespace-annotated classes.
     */
    @Pointcut("within(@org.ngmon.logger.annotation.Namespace *) && execution(* *(..))")
    public void allMethodsInNamespace() {}
    
    /**
     * Finds out method name and its parameter and injects the parameters values, then returns control to the intercepted method.
     */
    @Around("allMethodsInNamespace()")
    public AbstractNamespace aroundAllMethodsInNamespace(ProceedingJoinPoint thisJoinPoint) {

        MethodSignature method = (MethodSignature) thisJoinPoint.getSignature();
        ((AbstractNamespace)(thisJoinPoint.getTarget())).inject(method.getName(), method.getParameterNames(), thisJoinPoint.getArgs());

        try {
            return (AbstractNamespace)(thisJoinPoint.proceed());
        } catch (Throwable ex) {
	        ex.printStackTrace();
            return null;
        }
    }
}
