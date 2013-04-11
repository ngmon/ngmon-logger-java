package cz.muni.fi.sampleproject;

import cz.muni.fi.logger.LoggerFactory;

/**
* Hello world!
*
*/
public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        
        SampleNamespace LOG = LoggerFactory.getLogger(SampleNamespace.class, new Log4jLogger());
        LOG.tag("EntityA").tag("EntityB").event1("abc", 123);
        LOG.tag("EntityA").event1("abc", 123);
        LOG.event1("abc", 123);
    }
}