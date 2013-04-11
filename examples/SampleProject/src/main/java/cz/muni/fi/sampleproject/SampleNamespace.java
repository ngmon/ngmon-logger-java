package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.AbstractNamespace;

@Namespace
public class SampleNamespace extends AbstractNamespace<SampleNamespace> {

    public void event1(String param1, int param2) {
        log(param1, param2);
    }

    public void event2(double b, boolean c, double a) {
        log(b, c, a);
    }
}
