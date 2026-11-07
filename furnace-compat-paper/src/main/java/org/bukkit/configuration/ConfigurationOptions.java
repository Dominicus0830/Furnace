package org.bukkit.configuration;

public class ConfigurationOptions {

    private final Configuration configuration;
    private char pathSeparator = '.';
    private boolean copyDefaults;

    protected ConfigurationOptions(Configuration configuration) { this.configuration = configuration; }

    public Configuration configuration() { return configuration; }

    public char pathSeparator() { return pathSeparator; }

    public ConfigurationOptions pathSeparator(char value) {
        if (value != '.') throw new UnsupportedOperationException("Furnace 호환 설정은 경로 구분자로 '.' 만 지원한다");
        this.pathSeparator = value;
        return this;
    }

    public boolean copyDefaults() { return copyDefaults; }

    public ConfigurationOptions copyDefaults(boolean value) {
        this.copyDefaults = value;
        return this;
    }
}
