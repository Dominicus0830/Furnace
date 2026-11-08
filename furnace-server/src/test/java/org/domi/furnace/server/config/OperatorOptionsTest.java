// 운영 옵션(O1): 기본값은 바닐라 동작, 잘못된 값은 기동 때 거부.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class OperatorOptionsTest {

    private static OperatorOptions parse(List<String> warns, String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return OperatorOptions.from(p, warns::add);
    }

    @Test
    void defaultsAreVanilla() {
        List<String> warns = new ArrayList<>();
        assertEquals(OperatorOptions.DEFAULT, parse(warns));
        assertTrue(warns.isEmpty());
        var d = OperatorOptions.DEFAULT;
        assertTrue(d.strictChatOrder());
        assertFalse(d.unsignedChatAsSystem());
        assertTrue(d.announceSleepStatus());
        assertTrue(d.gravityBlockPortalDupe(), "기본은 바닐라(복제 유지)");
        assertFalse(d.confirmInterrupt());
        assertEquals("Furnace", d.serverBrand());
    }

    @Test
    void readsKeys() {
        List<String> warns = new ArrayList<>();
        var o = parse(warns, "chat.strict-order", "false", "chat.unsigned-as-system", "TRUE", "server-brand", " MyNet ",
                "announce-sleep-status", "false", "confirm-interrupt", "true", "confirm-interrupt-seconds", "8");
        assertFalse(o.strictChatOrder());
        assertTrue(o.unsignedChatAsSystem());
        assertEquals("MyNet", o.serverBrand());
        assertFalse(o.announceSleepStatus());
        assertTrue(o.confirmInterrupt());
        assertEquals(8, o.interruptConfirmSeconds());
        assertFalse(parse(new ArrayList<>(), "gravity-block-portal-dupe", "false").gravityBlockPortalDupe());
        assertThrows(IllegalArgumentException.class, () -> parse(new ArrayList<>(), "gravity-block-portal-dupe", "no"));
        assertEquals(1, warns.size(), "채팅 신고 불가는 경고한다");
    }

    @Test
    void motdFormatDefaultsToPlainAndValidates() {
        assertEquals("plain", OperatorOptions.DEFAULT.motdFormat());
        assertEquals("markup", parse(new ArrayList<>(), "motd-format", " Markup ").motdFormat());
        assertEquals("legacy", parse(new ArrayList<>(), "motd-format", "legacy").motdFormat());
        assertThrows(IllegalArgumentException.class, () -> parse(new ArrayList<>(), "motd-format", "html"));
    }

    @Test
    void blankBrandFallsBack() {
        assertEquals("Furnace", parse(new ArrayList<>(), "server-brand", "  ").serverBrand());
    }

    @Test
    void rejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> parse(new ArrayList<>(), "chat.strict-order", "maybe"));
        assertThrows(IllegalArgumentException.class, () -> parse(new ArrayList<>(), "confirm-interrupt-seconds", "0"));
        assertThrows(IllegalArgumentException.class, () -> parse(new ArrayList<>(), "confirm-interrupt-seconds", "abc"));
    }
}
