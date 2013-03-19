package cz.muni.fi.sampleproject;

import cz.muni.fi.logger.LoggerFactory;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        
        SampleNamespace LOG = LoggerFactory.getLogger(SampleNamespace.class);
        LOG.event1("abc", 123).tag("EntityA").tag("EntityB").log();
        LOG.event1("abc", 123).tag("EntityA").debug();
        LOG.event1("abc", 123).error();
    }
}