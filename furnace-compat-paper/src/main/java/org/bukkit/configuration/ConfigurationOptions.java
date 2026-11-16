package org.bukkit.configuration;

public class ConfigurationOptions {

    private final Configuration configuration;
    private char pathSeparator = '.';
    private boolean copyDefaults;

    protected ConfigurationOptions(Configuration configuration) { this.configuration = configuration; }

    public Configuration configuration() { return configuration; }

    public char pathSeparator() { return pathSeparator; }

    /** 경로를 단계로 나누는 문자(기본 '.'). 바꾸면 그 뒤의 조회·저장이 새 문자로 경로를 나눈다 — 점이 든 키를 쓰는 설정에 쓴다(A15-05). */
    public ConfigurationOptions pathSeparator(char value) {
        this.pathSeparator = value;
        return this;
    }

    public boolean copyDefaults() { return copyDefaults; }

    public ConfigurationOptions copyDefaults(boolean value) {
        this.copyDefaults = value;
        return this;
    }
}
