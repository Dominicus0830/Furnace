// 운영 옵션(furnace.properties) — 바닐라와 결과가 달라지는 것은 모두 기본이 바닐라 동작이다. 서버 하나에 한 벌, 기동 때 정해진다.
package org.domi.furnace.server.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * strictChatOrder: true(바닐라)면 순서가 어긋난 채팅(시각이 이전 메시지보다 앞)을 만나면 서명 체인을 끊는다 — 그 세션은 재접속 전까지 채팅·서명 명령이 막힌다.
 *   false 면 그 메시지(또는 명령)만 버리고 체인은 이어 간다.
 * unsignedChatAsSystem: true 면 플레이어 채팅을 서명 없는 시스템 메시지로 보낸다(클라이언트 채팅 신고·"Not Secure" 표시 대상이 아니게 된다). 기본 false.
 * serverBrand: 클라이언트 F3 의 서버 브랜드와 level.dat ServerBrands. 기본은 Furnace 의 기존 값.
 * announceSleepStatus: false 면 자는 사람 수("x/y 명 자는 중")·"밤을 건너뛰는 중" 액션바를 보내지 않는다. 기본 true(바닐라).
 * motdFormat: server.properties 의 motd 를 어떻게 읽을지. plain(기본, 바닐라 — 글자 그대로, § 코드는 클라이언트가 해석) | legacy(& 색 코드·&#RRGGBB) | markup(MiniMessage 식 태그: <gradient:red:blue>…</gradient>).
 *   잘못된 마크업은 경고 후 글자 그대로 보낸다.
 * confirmInterrupt: true 면 콘솔 Ctrl+C(SIGINT) 를 한 번 누르면 안내만 하고, interruptConfirmSeconds 안에 한 번 더 눌러야 종료한다. 기본 false(바로 저장 후 종료).
 */
public record OperatorOptions(boolean strictChatOrder, boolean unsignedChatAsSystem, String serverBrand,
                              boolean announceSleepStatus, boolean confirmInterrupt, int interruptConfirmSeconds,
                              String motdFormat) {

    public static final String DEFAULT_BRAND = "Furnace";
    public static final OperatorOptions DEFAULT = new OperatorOptions(true, false, DEFAULT_BRAND, true, false, 5, "plain");

    private static volatile OperatorOptions current = DEFAULT;

    /** 지금 적용 중인 옵션(기동 때 {@link #install} 로 바뀐다). */
    public static OperatorOptions current() { return current; }

    public static void install(OperatorOptions o) { current = o == null ? DEFAULT : o; }

    public static OperatorOptions load(Path path) {
        return load(path, ConfigLoader.log()::warn);
    }

    static OperatorOptions load(Path path, Consumer<String> warn) {
        if (!Files.exists(path)) return DEFAULT;
        return from(FurnaceProps.read(path), warn);
    }

    static OperatorOptions from(Properties props, Consumer<String> warn) {
        boolean strict = bool(props, "chat.strict-order", DEFAULT.strictChatOrder);
        boolean asSystem = bool(props, "chat.unsigned-as-system", DEFAULT.unsignedChatAsSystem);
        String brand = props.getProperty("server-brand", "").strip();
        if (brand.isEmpty()) brand = DEFAULT_BRAND;
        boolean announce = bool(props, "announce-sleep-status", DEFAULT.announceSleepStatus);
        boolean confirm = bool(props, "confirm-interrupt", DEFAULT.confirmInterrupt);
        int seconds = DEFAULT.interruptConfirmSeconds;
        String raw = props.getProperty("confirm-interrupt-seconds");
        if (raw != null) {
            try {
                seconds = Integer.parseInt(raw.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("설정 'confirm-interrupt-seconds' 가 숫자가 아님: " + raw, e);
            }
            if (seconds < 1 || seconds > 60) {
                throw new IllegalArgumentException("설정 'confirm-interrupt-seconds' 값 " + seconds + " 이(가) 잘못됐습니다: 1~60");
            }
        }
        String motdFormat = props.getProperty("motd-format", DEFAULT.motdFormat).strip().toLowerCase(java.util.Locale.ROOT);
        if (!motdFormat.equals("plain") && !motdFormat.equals("legacy") && !motdFormat.equals("markup")) {
            throw new IllegalArgumentException("설정 'motd-format' 값 '" + motdFormat + "' 이(가) 잘못됐습니다: plain, legacy, markup 중 하나");
        }
        if (asSystem) warn.accept("chat.unsigned-as-system=true: 채팅이 서명 없는 시스템 메시지로 나갑니다(채팅 신고 불가, 채팅 서명 검증 없음)");
        return new OperatorOptions(strict, asSystem, brand, announce, confirm, seconds, motdFormat);
    }

    private static boolean bool(Properties props, String key, boolean def) {
        String v = props.getProperty(key);
        if (v == null) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
        }
        return Boolean.parseBoolean(v);
    }
}
