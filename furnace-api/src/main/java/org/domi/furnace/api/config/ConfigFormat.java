// 지원하는 설정 파일 포맷(YAML/TOML/JSON)과 파싱·직렬화·확장자 감지
package org.domi.furnace.api.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 설정 포맷. 외부 의존성 없이 자체 구현한 파서/작성기를 사용한다. 기본 포맷은 {@link #YAML}.
 *
 * <p>파싱 실패는 줄/열이 담긴 {@link ConfigParseException}(비검사)으로 던진다.
 */
public enum ConfigFormat {

    /**
     * YAML 1.1 서브셋(Bukkit/SnakeYAML 호환), 확장자 .yml/.yaml, 주석 보존.
     *
     * <p>지원: 블록/플로우 컬렉션, 평문·홑따옴표·겹따옴표 스칼라(여러 줄 포함), {@code | >} 블록 스칼라(+ - 및 들여쓰기 표시),
     * anchor/alias/병합 키({@code <<}), {@code !!str !!int !!float !!bool !!null} 태그, {@code ---} / {@code ...}.
     * 불리언은 yes/no/on/off/true/false(대소문자 무시), 정수는 10/16(0x)/8(0o, 앞 0)/2(0b)진과 {@code _} 구분자.
     *
     * <p>SnakeYAML 과의 차이: 키는 항상 원문 문자열(on/yes/1 키가 Boolean/Integer 로 바뀌지 않음), 60진수(12:30)와
     * timestamp 는 해석하지 않고 문자열로 둠, long 범위를 넘는 정수는 문자열로 보존, 중복 키·탭 들여쓰기는 오류,
     * {@code ? } 복합 키와 문서 여러 개는 미지원, 알 수 없는 태그는 무시. 리스트 항목 앞 주석은 보존하지 않는다.
     * 쓸 때 리스트는 키보다 2칸 들여 쓰고, 문자열은 필요할 때만 홑따옴표로 감싼다.
     */
    YAML("yml", "yaml"),
    /**
     * TOML v1.0.0, 확장자 .toml, 주석 보존(테이블 배열 원소 앞 주석은 원소의 {@link ConfigSection#getHeader()}).
     * 날짜/시간은 java.time 타입(OffsetDateTime/LocalDateTime/LocalDate/LocalTime)으로 저장한다.
     * 정수는 Integer(범위 내) 또는 Long. TOML 에는 null 이 없어 쓸 때 null 값은 생략하고, 값이 테이블보다 먼저 오도록 재배치한다.
     */
    TOML("toml"),
    /** 엄격한 JSON(RFC 8259). 확장자 .json. 주석 없음. */
    JSON("json");

    private final List<String> extensions;

    ConfigFormat(String... extensions) {
        this.extensions = List.of(extensions);
    }

    /** 이 포맷의 확장자들(점 제외, 소문자). 첫 번째가 기본 확장자. */
    public List<String> extensions() { return extensions; }

    public String defaultExtension() { return extensions.get(0); }

    /** 문자열을 파싱한다. 루트는 매핑(객체)이어야 한다. */
    public ConfigSection parse(String text) {
        return switch (this) {
            case YAML -> YamlParser.parse(text);
            case TOML -> TomlParser.parse(text);
            case JSON -> JsonParser.parse(text);
        };
    }

    /** Reader 전체를 읽어 파싱한다(닫지는 않는다). */
    public ConfigSection parse(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
        return parse(sb.toString());
    }

    /** 섹션을 이 포맷의 텍스트로 직렬화한다. */
    public String write(ConfigSection section) {
        return switch (this) {
            case YAML -> YamlWriter.write(section);
            case TOML -> TomlWriter.write(section);
            case JSON -> JsonWriter.write(section);
        };
    }

    /** 파일 이름의 확장자(.yml/.yaml/.toml/.json, 대소문자 무시)로 포맷을 찾는다. */
    public static Optional<ConfigFormat> fromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return Optional.empty();
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (ConfigFormat f : values()) if (f.extensions.contains(ext)) return Optional.of(f);
        return Optional.empty();
    }

    public static Optional<ConfigFormat> fromPath(Path path) {
        Path name = path.getFileName();
        return name == null ? Optional.empty() : fromFileName(name.toString());
    }
}
