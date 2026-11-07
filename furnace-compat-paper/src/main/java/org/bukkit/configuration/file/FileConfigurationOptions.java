package org.bukkit.configuration.file;

import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.MemoryConfigurationOptions;

public class FileConfigurationOptions extends MemoryConfigurationOptions {

    private boolean copyHeader = true;
    private boolean parseComments = true;

    protected FileConfigurationOptions(MemoryConfiguration configuration) { super(configuration); }

    @Override
    public FileConfiguration configuration() { return (FileConfiguration) super.configuration(); }

    @Override
    public FileConfigurationOptions copyDefaults(boolean value) {
        super.copyDefaults(value);
        return this;
    }

    @Override
    public FileConfigurationOptions pathSeparator(char value) {
        super.pathSeparator(value);
        return this;
    }

    /** 파일 맨 위 주석(줄바꿈으로 나뉜다). 없으면 null. */
    public String header() {
        List<String> h = configuration().getHeaderLines();
        return h.isEmpty() ? null : String.join("\n", h);
    }

    public FileConfigurationOptions header(String value) {
        configuration().setHeaderLines(value == null ? List.of() : Arrays.asList(value.split("\n", -1)));
        return this;
    }

    public List<String> getHeader() { return configuration().getHeaderLines(); }

    public FileConfigurationOptions setHeader(List<String> value) {
        configuration().setHeaderLines(value == null ? List.of() : value);
        return this;
    }

    public List<String> getFooter() { return configuration().getFooterLines(); }

    public FileConfigurationOptions setFooter(List<String> value) {
        configuration().setFooterLines(value == null ? List.of() : value);
        return this;
    }

    public boolean copyHeader() { return copyHeader; }

    public FileConfigurationOptions copyHeader(boolean value) {
        this.copyHeader = value;
        return this;
    }

    public boolean parseComments() { return parseComments; }

    public FileConfigurationOptions parseComments(boolean value) {
        this.parseComments = value;
        return this;
    }
}
