// 바닐라 Settings(server.properties 공통 로직) 이식. 읽은 키마다 정규화한 값을 다시 넣어 두고(store 시 기본값이 채워진다),
// 숫자 파싱 실패는 기본값, 불리언은 Boolean.valueOf(알 수 없는 문자열은 false), 모르는 키는 그대로 보존한다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class VanillaSettings {

    private static final Logger log = LoggerFactory.getLogger(VanillaSettings.class);

    protected final Properties properties;

    protected VanillaSettings(Properties properties) {
        this.properties = properties;
    }

    /** UTF-8 로 읽고, 디코딩 오류면 ISO-8859-1 로 다시 읽는다. 파일이 없거나 읽기 실패면 빈 Properties. */
    public static Properties loadFromFile(Path path) {
        try {
            try (InputStream in = Files.newInputStream(path)) {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                Properties p = new Properties();
                p.load(new InputStreamReader(in, decoder));
                return p;
            } catch (CharacterCodingException e) {
                log.info("Failed to load properties as UTF-8 from file {}, trying ISO_8859_1", path);
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                    Properties p = new Properties();
                    p.load(r);
                    return p;
                }
            }
        } catch (IOException e) {
            log.error("Failed to load properties from file: {}", path, e);
            return new Properties();
        }
    }

    /** UTF-8 로 저장. JDK 18+ Properties.store 는 키를 정렬해 쓰므로 바닐라 파일과 같은 순서가 된다. */
    public void store(Path path) {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(w, "Minecraft server properties");
        } catch (IOException e) {
            log.error("Failed to store properties to file: {}", path);
        }
    }

    /** 현재 값들의 사본(바닐라 cloneProperties). */
    public Properties cloneProperties() {
        Properties p = new Properties();
        p.putAll(properties);
        return p;
    }

    static <V extends Number> Function<String, V> wrapNumberDeserializer(Function<String, V> f) {
        return s -> {
            try {
                return f.apply(s);
            } catch (NumberFormatException e) {
                return null;
            }
        };
    }

    /** 정수면 id 로, 아니면 이름으로 해석(difficulty·gamemode). */
    static <V> Function<String, V> dispatchNumberOrString(IntFunction<V> byId, Function<String, V> byName) {
        return s -> {
            try {
                return byId.apply(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return byName.apply(s);
            }
        };
    }

    private String getStringRaw(String key) {
        return (String) properties.get(key);
    }

    /** 레거시 키: 있으면 읽고 제거한다(다시 쓰지 않는다). */
    protected <V> V getLegacy(String key, Function<String, V> parse) {
        String raw = getStringRaw(key);
        if (raw == null) return null;
        properties.remove(key);
        return parse.apply(raw);
    }

    protected <V> V get(String key, Function<String, V> parse, Function<V, String> serialize, V def) {
        String raw = getStringRaw(key);
        V parsed = raw != null ? parse.apply(raw) : null;
        V v = parsed != null ? parsed : def;
        properties.put(key, serialize.apply(v));
        return v;
    }

    protected <V> MutableValue<V> getMutable(String key, Function<String, V> parse, Function<V, String> serialize, V def) {
        V v = get(key, parse, serialize, def);
        return new MutableValue<>(key, v, serialize);
    }

    protected <V> V get(String key, Function<String, V> parse, UnaryOperator<V> modifier, Function<V, String> serialize, V def) {
        return get(key, s -> {
            V v = parse.apply(s);
            return v != null ? modifier.apply(v) : null;
        }, serialize, def);
    }

    protected <V> V get(String key, Function<String, V> parse, V def) {
        return get(key, parse, Objects::toString, def);
    }

    protected String get(String key, String def) {
        return get(key, Function.identity(), Function.identity(), def);
    }

    protected String getLegacyString(String key) {
        return getLegacy(key, Function.identity());
    }

    protected int get(String key, int def) {
        return get(key, wrapNumberDeserializer(Integer::parseInt), def);
    }

    protected int get(String key, UnaryOperator<Integer> modifier, int def) {
        return get(key, wrapNumberDeserializer(Integer::parseInt), modifier, Objects::toString, def);
    }

    protected long get(String key, long def) {
        return get(key, wrapNumberDeserializer(Long::parseLong), def);
    }

    protected boolean get(String key, boolean def) {
        return get(key, Boolean::valueOf, def);
    }

    protected Boolean getLegacyBoolean(String key) {
        return getLegacy(key, Boolean::valueOf);
    }

    /** 런타임에 바뀌어 파일에 다시 쓰이는 값(white-list, player-idle-timeout). */
    public final class MutableValue<V> {
        private final String key;
        private final V value;
        private final Function<V, String> serialize;

        MutableValue(String key, V value, Function<V, String> serialize) {
            this.key = key;
            this.value = value;
            this.serialize = serialize;
        }

        public V get() { return value; }

        /** 이 키만 바꾼 새 Properties(바닐라 MutableValue.update 의 재구성 입력). */
        public Properties with(V v) {
            Properties p = cloneProperties();
            p.put(key, serialize.apply(v));
            return p;
        }
    }
}
