// MOTD 해석 — furnace.properties 의 motd-format 에 따라 server.properties 의 motd 문자열을 Text 로 만든다.
// plain(기본)은 바닐라와 같다: 글자 그대로(Component.literal) 보내고 § 코드는 클라이언트가 해석한다.
package org.domi.furnace.server.net;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.markup.TextMarkup;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;
import org.domi.furnace.server.config.OperatorOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Motd {

    private static final Logger log = LoggerFactory.getLogger(Motd.class);
    private static volatile String warned;

    private Motd() {}

    /** 지금 적용 중인 motd-format 으로 해석한다. */
    public static Text parse(String raw) { return parse(raw, OperatorOptions.current().motdFormat()); }

    public static Text parse(String raw, String format) {
        switch (format) {
            case "legacy":
                return LegacyTextSerializer.ampersand().deserialize(raw);
            case "markup":
                try {
                    return TextMarkup.markup().deserialize(raw);
                } catch (RuntimeException e) {
                    if (!raw.equals(warned)) {
                        warned = raw;
                        log.warn("motd 마크업을 읽을 수 없어 글자 그대로 보냅니다: {}", e.getMessage());
                    }
                    return Text.text(raw);
                }
            default:
                return Text.text(raw);
        }
    }

    /** plain 이 아니면 true — plain 은 상태 응답 JSON 도 바닐라처럼 문자열 그대로 둔다. */
    public static boolean isRich() { return !"plain".equals(OperatorOptions.current().motdFormat()); }
}
