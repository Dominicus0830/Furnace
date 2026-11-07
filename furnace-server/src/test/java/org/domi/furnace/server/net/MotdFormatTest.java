// C2(불만 표 66번): motd-format — plain(바닐라, 기본)·legacy(& 코드)·markup(MiniMessage 식 태그).
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.config.OperatorOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MotdFormatTest {

    @AfterEach
    void reset() { OperatorOptions.install(null); }

    private static void useFormat(String f) {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), f));
    }

    @Test
    void defaultIsPlainAndStatusJsonIsVanillaString() {
        assertEquals("plain", OperatorOptions.DEFAULT.motdFormat());
        Text t = Motd.parse("<red>hi &a");
        assertEquals("<red>hi &a", t.plainText());
        var ctx = ServerLoginContextAccess.offline("<red>hi");
        String json = ServerLoginContextAccess.statusJson(ctx, org.domi.furnace.server.plugin.GameEvents.NONE);
        assertTrue(json.contains("\"description\":\"<red>hi\""), json);
    }

    @Test
    void markupFormatBuildsStyledTextAndJsonDescription() {
        useFormat("markup");
        Text t = Motd.parse("<red>Hello <bold>World</bold>");
        assertEquals("Hello World", t.plainText());
        var ctx = ServerLoginContextAccess.offline("<gradient:red:blue>Furnace</gradient>");
        String json = ServerLoginContextAccess.statusJson(ctx, org.domi.furnace.server.plugin.GameEvents.NONE);
        assertTrue(json.contains("\"color\":\"#"), json);
        assertTrue(json.contains("\"description\":{") || json.contains("\"description\":["), json);
    }

    @Test
    void legacyFormatReadsAmpersandCodes() {
        useFormat("legacy");
        Text t = Motd.parse("&6Gold &lbold");
        assertEquals("Gold bold", t.plainText());
        assertTrue(t.toJson().contains("gold"), t.toJson());
    }

    @Test
    void brokenMarkupFallsBackToPlainText() {
        useFormat("markup");
        assertEquals("<click:nope>x", Motd.parse("<click:nope>x").plainText().replace("&lt;", "<"));
    }
}
