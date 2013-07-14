package org.ngmon.logger.util;

import java.util.ArrayList;
import java.util.List;

public class TestJsoner {

    public static void main(String[] args) {

        String fqns = "log_events/org/ngmon/examples/L_Connections";
        String method = "unableToConnect";

        String[] names = new String[]{"host", "user", "attemptsLeft"};
        Object[] values = new Object[]{"localhost", "xtovarn", 523};

        List<String> tags = new ArrayList<>();
        tags.add("tag1");
        tags.add("tag2");
        tags.add("tag3");

        System.out.println(JSONer.getEventJson(fqns, method, tags, names, values, 10));
    }
}
