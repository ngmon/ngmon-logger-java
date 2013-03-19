package cz.muni.fi.sampleproject;

import cz.muni.fi.logger.Logger;
import cz.muni.fi.logger.LoggerFactory;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        
        Logger<SampleNamespace> LOG = LoggerFactory.getLogger(SampleNamespace.class);
        LOG.error().tag("entityA").tag("entityB").event1("aaa", 222);
        LOG.debug().event2(1, 2, true);
    }
}
