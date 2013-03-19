package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.AbstractNamespace;

@Namespace
public class SampleNamespace extends AbstractNamespace {

    public AbstractNamespace event1(String param1, int param2) {
        return log("event1", new String[]{"param1","param2"}, param1, param2);
    }

    public AbstractNamespace event2(double b, boolean c, double a) {
        return log("event2", new String[]{"b","c","a"}, b, c, a);
    }
}