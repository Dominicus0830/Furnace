// 바닐라 Eula 이식 — eula.txt 를 ISO-8859-1 Properties 로 읽고, 없거나 읽기 실패면 eula=false 기본 파일을 만든다.
package org.domi.furnace.server.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Eula {

    private static final Logger log = LoggerFactory.getLogger(Eula.class);
    public static final String EULA_URL = "https://aka.ms/MinecraftEULA";

    private final Path file;
    private final boolean agreed;

    public Eula(Path file) {
        this.file = file;
        this.agreed = readFile();
    }

    private boolean readFile() {
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            return Boolean.parseBoolean(p.getProperty("eula", "false"));
        } catch (Exception e) {
            log.warn("Failed to load {}", file);
            saveDefaults();
            return false;
        }
    }

    public boolean hasAgreedToEULA() { return agreed; }

    private void saveDefaults() {
        try (OutputStream out = Files.newOutputStream(file)) {
            Properties p = new Properties();
            p.setProperty("eula", "false");
            p.store(out, "By changing the setting below to TRUE you are indicating your agreement to our EULA (" + EULA_URL + ").");
        } catch (Exception e) {
            log.warn("Failed to save {}", file, e);
        }
    }
}
