package org.ngmon.logger.core;

import java.util.List;

public interface Logger {
    
	public void log(String fqnNS, String methodName, List<String> tags, String[] paramNames, Object[] paramValues, int level);
}
