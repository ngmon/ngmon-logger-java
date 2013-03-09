package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.Logger;

@Namespace
public class SampleNamespace extends Logger<SampleNamespace> {

    private static final String FQN = SampleNamespace.class.getCanonicalName();

    public void event1(String param1, int param2) {
        log(FQN, "event1", new String[]{"param1","param2"}, param1, param2);
    }

    public void event2(double a, double b, boolean c) {
        log(FQN, "event2", new String[]{"a","b","c"}, a, b, c);
    }
}
