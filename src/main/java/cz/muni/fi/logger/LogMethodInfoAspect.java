package cz.muni.fi.logger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class LogMethodInfoAspect {
    
    @Pointcut("within(cz.muni.fi.logger.Logger+) && ! within(cz.muni.fi.logger.Logger) && execution(* *(..))")
    public void allMethodsInEntities() {}
    
    @Around("allMethodsInEntities()")
    public Logger aroundAllMethodsInEntities(ProceedingJoinPoint thisJoinPoint) {
        MethodSignature method = (MethodSignature) thisJoinPoint.getSignature();
        ((Logger)(thisJoinPoint.getTarget())).setNames(method.getDeclaringType().getSimpleName(), 
                method.getDeclaringType().getPackage().getName(), method.getName(), method.getParameterNames());
        try {
            return (Logger)(thisJoinPoint.proceed(thisJoinPoint.getArgs()));
        } catch (Throwable ex) {
            return null;
        }
    }
}
