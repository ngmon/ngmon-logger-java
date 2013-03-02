package cz.muni.fi.sampleproject;

import cz.muni.fi.annotation.SourceNamespace;

public class L_EntityXY extends L_Entity {
    
    @SourceNamespace("cz.muni.fi.sampleproject.SampleNamespace")
    private enum SampleNamespaceEvents {
        event1, abc
    }
    
    @SourceNamespace("a.b.c.d.NamespaceX")
    private enum NoNamespaceEvents { //will not have any effect, since this @SourceNamespace does not exist
        eventX
    }
    
    private enum MyEnum { //no @SourceNamespace - ignored by the processor
        X, Y, Z
    }
}
