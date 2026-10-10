// 파일에 연결된 설정 — UTF-8 로드, 원자적 저장(임시 파일 후 이동), reload, 기본값 병합
package org.domi.furnace.api.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 파일 하나와 연결된 설정. 포맷은 확장자로 감지하며(알 수 없으면 YAML) 명시할 수도 있다.
 * 실제 데이터는 {@link #root()} 의 {@link ConfigSection} 트리에 있다.
 */
public final class Config {

    private final Path path;
    private final ConfigFormat format;
    private ConfigSection root;

    private Config(Path path, ConfigFormat format, ConfigSection root) {
        this.path = path;
        this.format = format;
        this.root = root;
    }

    /** 파일을 읽는다. 없으면 NoSuchFileException, 구문 오류면 ConfigParseException(파일 이름 포함). */
    public static Config load(Path path) throws IOException {
        return load(path, ConfigFormat.fromPath(path).orElse(ConfigFormat.YAML));
    }

    public static Config load(Path path, ConfigFormat format) throws IOException {
        return new Config(path, format, read(path, format));
    }

    /** 파일이 없으면 빈 설정으로 시작한다(저장 시 생성). */
    public static Config loadOrEmpty(Path path) throws IOException {
        ConfigFormat format = ConfigFormat.fromPath(path).orElse(ConfigFormat.YAML);
        try {
            return load(path, format);
        } catch (NoSuchFileException e) {
            return new Config(path, format, new ConfigSection());
        }
    }

    /** 파일과 연결된 빈 설정을 만든다(아직 디스크에 쓰지 않음). */
    public static Config empty(Path path) {
        return new Config(path, ConfigFormat.fromPath(path).orElse(ConfigFormat.YAML), new ConfigSection());
    }

    private static ConfigSection read(Path path, ConfigFormat format) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        try {
            return format.parse(text);
        } catch (ConfigParseException e) {
            throw e.withSource(String.valueOf(path.getFileName()));
        }
    }

    public Path path() { return path; }

    public ConfigFormat format() { return format; }

    /** 루트 섹션. reload() 후에는 새 인스턴스로 바뀌므로 오래 들고 있지 않는다. */
    public ConfigSection root() { return root; }

    /** 디스크에서 다시 읽는다. 기존 defaults 는 유지된다. 실패하면 현재 내용은 그대로다. */
    public void reload() throws IOException {
        ConfigSection fresh = read(path, format);
        fresh.setDefaults(root.getDefaults());
        root = fresh;
    }

    /** 기본값 트리 설정 (조회 시 값이 없으면 여기서 찾는다). */
    public void setDefaults(ConfigSection defaults) { root.setDefaults(defaults); }

    public ConfigSection defaults() { return root.getDefaults(); }

    /** 기본값에만 있는 키를 실제 값으로 채운다(주석 포함). 추가한 키 수를 반환. */
    public int copyDefaults() { return root.copyDefaults(); }

    /** 연결된 경로에 원자적으로 저장한다. */
    public void save() throws IOException {
        save(path);
    }

    /** 임시 파일에 쓴 뒤 이동해 저장하므로 도중에 죽어도 기존 파일이 깨지지 않는다. 상위 디렉터리는 자동 생성. */
    public void save(Path target) throws IOException {
        byte[] bytes = format.write(root).getBytes(StandardCharsets.UTF_8);
        Path abs = target.toAbsolutePath();
        Path dir = abs.getParent();
        if (dir != null) Files.createDirectories(dir);
        // 임시 파일은 기본 권한(umask)으로 만든다 — createTempFile 은 600 권한이라 저장 후 파일 권한이 바뀐다
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.wrap(bytes);
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }
            try {
                Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
