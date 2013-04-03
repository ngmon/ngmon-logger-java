package cz.muni.fi.logger;

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
    @Pointcut("within(@cz.muni.fi.annotation.Namespace *) && execution(* *(..))")
    public void allMethodsInNamespace() {}
    
    /**
     * Finds out method name and its parameter names so only the values of parameters need to be sent to the logger;
     * then returns control to the intercepted method.
     */
    @Around("allMethodsInNamespace()")
    public AbstractNamespace aroundAllMethodsInNamespace(ProceedingJoinPoint thisJoinPoint) {
        MethodSignature method = (MethodSignature) thisJoinPoint.getSignature();
        ((AbstractNamespace)(thisJoinPoint.getTarget())).setNames(method.getName(), method.getParameterNames());
        try {
            return (AbstractNamespace)(thisJoinPoint.proceed(thisJoinPoint.getArgs()));
        } catch (Throwable ex) {
            return null;
        }
    }
}
