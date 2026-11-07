package org.bukkit.configuration.file;

public class YamlConfigurationOptions extends FileConfigurationOptions {

    private int indent = 2;
    private int width = 80;

    protected YamlConfigurationOptions(YamlConfiguration configuration) { super(configuration); }

    @Override
    public YamlConfiguration configuration() { return (YamlConfiguration) super.configuration(); }

    @Override
    public YamlConfigurationOptions copyDefaults(boolean value) {
        super.copyDefaults(value);
        return this;
    }

    @Override
    public YamlConfigurationOptions pathSeparator(char value) {
        super.pathSeparator(value);
        return this;
    }

    @Override
    public YamlConfigurationOptions header(String value) {
        super.header(value);
        return this;
    }

    @Override
    public YamlConfigurationOptions copyHeader(boolean value) {
        super.copyHeader(value);
        return this;
    }

    @Override
    public YamlConfigurationOptions parseComments(boolean value) {
        super.parseComments(value);
        return this;
    }

    /** 들여쓰기 칸 수(저장 형식은 Furnace 의 YAML 작성기가 정하므로 기록만 한다). */
    public int indent() { return indent; }

    public YamlConfigurationOptions indent(int value) {
        if (value < 2 || value > 9) throw new IllegalArgumentException("indent 는 2~9");
        this.indent = value;
        return this;
    }

    public int width() { return width; }

    public YamlConfigurationOptions width(int value) {
        this.width = value;
        return this;
    }
}
