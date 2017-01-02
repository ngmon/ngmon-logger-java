package org.ngmon.logger.level;

public enum Level {
    DEBUG("DEBUG"),
    WARN("WARN"),
    TRACE("TRACE"),
    FATAL("FATAL"),
    ERROR("ERROR"),
    INFO("INFO");

    private final String value;

    Level(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}