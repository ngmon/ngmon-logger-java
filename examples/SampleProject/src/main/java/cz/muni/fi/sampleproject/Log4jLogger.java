package cz.muni.fi.sampleproject;

import cz.muni.fi.logger.Logger;
import org.apache.logging.log4j.LogManager;

public class Log4jLogger implements Logger {
    
    private org.apache.logging.log4j.Logger LOG = LogManager.getLogger(Log4jLogger.class);

    @Override
    public void log(String message) {
        LOG.debug(message);
    }
    
}
