package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.Namespace;
import cz.muni.fi.logger.AbstractNamespace;

@Namespace
public class SampleNamespace extends AbstractNamespace {

    public AbstractNamespace event1(String param1, int param2) {
        return log(param1, param2);
    }

    public AbstractNamespace event2(double a, double b, boolean c) {
        return log(a, b, c);
    }
}