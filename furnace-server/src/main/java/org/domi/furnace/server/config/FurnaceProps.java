// furnace.properties 를 읽는 단 하나의 입구. 모든 설정 로더가 여기를 거쳐 같은 값을 본다.
//   1. 파일을 읽는다(Properties.load, 바닐라 server.properties 와 같은 형식).
//   2. 값 안의 ${NAME} · ${NAME:-기본값} 을 환경 변수로 바꾼다(비밀값을 파일에 적지 않으려는 용도). 두 달러 + ${ 는 글자 그대로 ${ 이다.
//   3. config.preset 이 있으면 파일에 없는 키를 프리셋 값으로 채운다(파일에 적은 키가 항상 이긴다).
// 환경 변수가 없고 기본값도 없으면 어느 키가 문제인지 말하며 기동을 멈춘다 — 조용히 빈 글자로 두면 비밀번호가 빈 채 켜질 수 있다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FurnaceProps {

    private FurnaceProps() {}

    /** 이스케이프: 이 글자는 ${ 그대로 나온다. */
    private static final String ESCAPE = "$" + "${";

    /** 환경 변수 조회(시험에서 바꿔 끼운다). */
    private static volatile Function<String, String> env = System::getenv;

    static void setEnvForTest(Function<String, String> f) { env = f == null ? System::getenv : f; }

    /** 파일이 없으면 빈 Properties(프리셋만 적용). 읽기 실패는 RuntimeException, 환경 변수·프리셋 값이 잘못되면 IllegalArgumentException. */
    public static Properties read(Path file) {
        Properties p = substitute(readRaw(file), env, true, null);
        ConfigPresets.apply(p);
        return p;
    }

    /** 치환·프리셋 없이 파일 그대로(덤프·이전 도구용). 파일이 없으면 빈 Properties. */
    public static Properties readRaw(Path file) {
        Properties p = new Properties();
        if (!Files.exists(file)) return p;
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + file, e);
        }
        return p;
    }

    /**
     * 값마다 ${...} 를 푼다. strict=false 면 풀 수 없는 ${NAME} 을 글자 그대로 두고 warn 에 알린다(server.properties 처럼
     * 바닐라 파일은 ${ 가 들어 있어도 기동이 멈추면 안 된다). 새 Properties 를 만들어 돌려준다.
     */
    public static Properties substitute(Properties in, Function<String, String> lookup, boolean strict, Consumer<String> warn) {
        Properties out = new Properties();
        for (String k : in.stringPropertyNames()) {
            out.setProperty(k, expand(k, in.getProperty(k), lookup, strict, warn));
        }
        return out;
    }

    static String expand(String key, String value, Function<String, String> lookup, boolean strict, Consumer<String> warn) {
        if (value.indexOf("${") < 0) return value;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (value.startsWith(ESCAPE, i)) {
                sb.append("${");
                i += ESCAPE.length();
                continue;
            }
            if (value.startsWith("${", i)) {
                int end = value.indexOf('}', i + 2);
                if (end < 0) {
                    // 닫히지 않은 ${ 는 옛 파일의 평범한 글자일 수 있다 — 기동을 막지 않고 그대로 둔다
                    if (warn != null) warn.accept("'" + key + "' 의 '${' 가 닫히지 않아 글자 그대로 둡니다");
                    sb.append(value, i, value.length());
                    break;
                }
                String body = value.substring(i + 2, end);
                String name = body;
                String def = null;
                int sep = body.indexOf(":-");
                if (sep >= 0) {
                    name = body.substring(0, sep);
                    def = body.substring(sep + 2);
                }
                String v = name.isEmpty() ? null : lookup.apply(name);
                if (v == null || v.isEmpty()) v = def;   // 빈 환경 변수도 '없음' 으로 본다(${X:-기본} 이 흔히 기대하는 동작)
                if (v == null) {
                    if (strict) {
                        throw new IllegalArgumentException("설정 '" + key + "' 가 환경 변수 '" + name + "' 를 가리키는데 값이 없습니다"
                                + "(기본값을 주려면 ${" + name + ":-값}, 글자 그대로 쓰려면 '" + ESCAPE + "')");
                    }
                    if (warn != null) warn.accept("'" + key + "' 의 ${" + name + "} 는 환경 변수가 없어 글자 그대로 둡니다");
                    sb.append(value, i, end + 1);
                } else {
                    sb.append(v);
                }
                i = end + 1;
                continue;
            }
            sb.append(value.charAt(i++));
        }
        return sb.toString();
    }

    /** 원본 값에 풀어야 할 ${...} 가 들어 있는가(덤프의 '환경 변수에서' 표시용). */
    public static boolean usesEnv(String rawValue) {
        return rawValue != null && rawValue.replace(ESCAPE, "").contains("${");
    }
}
