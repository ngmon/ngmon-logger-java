package cz.muni.fi.sampleproject;

import LOGGER.L_EntityA;
import LOGGER.x.y.L_EntityC;
import cz.muni.fi.logger.Logger;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        
        Logger.initLoggers("EntityA","x.y.EntityC");
//        Logger.initAll();
        
        L_EntityA.method1("abc", true);
        L_EntityC.method3("abc", "def", "ghi");
    }
}
