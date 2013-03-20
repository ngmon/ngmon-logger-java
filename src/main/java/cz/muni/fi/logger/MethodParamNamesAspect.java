package cz.muni.fi.logger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class MethodParamNamesAspect {
    
    @Pointcut("within(@cz.muni.fi.annotation.Namespace *) && execution(* *(..))")
    public void allMethodsInNamespace() {}
    
    @Around("allMethodsInNamespace()")
    //TODO vyriesit: vracat AbstractNamespace je asi odvazne, ked to tym Pointcutom nie je nijak vymedzene...
    //(ale tak vsetky metody v @Namespace by to mali vracat, a ked sem dam Object, pokazi sa mi Fluent API :/ )
    public AbstractNamespace aroundAllMethodsInNamespace(ProceedingJoinPoint thisJoinPoint) {
        //TODO posielat aj typy parametrov, aby sa to nemusel JSONer snazit uhadnut
        MethodSignature method = (MethodSignature) thisJoinPoint.getSignature();
        ((AbstractNamespace)(thisJoinPoint.getTarget())).setNames(method.getName(), method.getParameterNames());
        try {
            return (AbstractNamespace)(thisJoinPoint.proceed(thisJoinPoint.getArgs()));
        } catch (Throwable ex) {
            return null;
        }
    }
}
