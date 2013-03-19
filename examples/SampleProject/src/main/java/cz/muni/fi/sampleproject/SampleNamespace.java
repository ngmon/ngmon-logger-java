package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.AbstractNamespace;

@Namespace
public class SampleNamespace extends AbstractNamespace<SampleNamespace> {

    public void event1(String param1, int param2) {
        log("event1", new String[]{"param1","param2"}, param1, param2);
    }

    public void event2(double a, double b, boolean c) {
        log("event2", new String[]{"a","b","c"}, a, b, c);
    }
}
