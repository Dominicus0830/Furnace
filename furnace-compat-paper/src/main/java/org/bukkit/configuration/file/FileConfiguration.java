package org.bukkit.configuration.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;

/** 파일로 읽고 쓰는 설정. */
public abstract class FileConfiguration extends MemoryConfiguration {

    public FileConfiguration() {}

    public FileConfiguration(Configuration defaults) { super(defaults); }

    public void save(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("File 이 null");
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.writeString(file.toPath(), saveToString(), StandardCharsets.UTF_8);
    }

    public void save(String file) throws IOException { save(new File(file)); }

    public abstract String saveToString();

    public void load(File file) throws FileNotFoundException, IOException, InvalidConfigurationException {
        if (file == null) throw new IllegalArgumentException("File 이 null");
        if (!file.exists()) throw new FileNotFoundException(file.getPath());
        loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    public void load(String file) throws FileNotFoundException, IOException, InvalidConfigurationException { load(new File(file)); }

    public void load(Reader reader) throws IOException, InvalidConfigurationException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            char[] buf = new char[4096];
            int n;
            while ((n = in.read(buf)) >= 0) sb.append(buf, 0, n);
        }
        loadFromString(sb.toString());
    }

    /** 문자열(YAML)에서 읽는다 — 이전 내용은 지워진다(기본값은 남는다). */
    public abstract void loadFromString(String contents) throws InvalidConfigurationException;

    @Override
    public abstract FileConfigurationOptions options();

    abstract List<String> getHeaderLines();

    abstract void setHeaderLines(List<String> lines);

    abstract List<String> getFooterLines();

    abstract void setFooterLines(List<String> lines);

    static Reader utf8(InputStream in) { return new InputStreamReader(in, StandardCharsets.UTF_8); }
}
