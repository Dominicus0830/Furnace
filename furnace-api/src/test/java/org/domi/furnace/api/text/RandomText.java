// 테스트 도구 — 시드 고정 난수로 임의의 텍스트 트리를 만든다(압축·직렬화 왕복 같은 성질 검사용).
package org.domi.furnace.api.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomText {

    private static final String[] WORDS = {"a", "bc", "hello", " ", "<", "\\", "'", ":", "\"", "한글", "x>y", "<red>", "😀", "\n", "%s", ""};
    private static final TextColor[] COLORS = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GOLD, TextColor.color(0x123456), TextColor.color(0xABCDEF)};

    private final Random rnd;
    private final boolean events;

    public RandomText(long seed, boolean events) {
        this.rnd = new Random(seed);
        this.events = events;
    }

    public Text next(int depth) {
        Style style = randomStyle();
        Content content = randomContent(depth);
        int kids = depth <= 0 ? 0 : rnd.nextInt(4);
        List<Text> children = new ArrayList<>();
        for (int i = 0; i < kids; i++) children.add(next(depth - 1));
        return Text.of(content, style, children);
    }

    private Content randomContent(int depth) {
        int r = rnd.nextInt(20);
        if (r == 0 && depth > 0) return new Content.Translatable("k." + rnd.nextInt(3), rnd.nextBoolean() ? "fb %s" : null, List.of(Text.text("arg", NamedTextColor.GREEN), 5, "s"));
        if (r == 1) return new Content.Keybind("key.jump");
        if (r == 2) return new Content.Score("@p", "obj");
        return new Content.Literal(WORDS[rnd.nextInt(WORDS.length)]);
    }

    private Style randomStyle() {
        Style s = Style.empty();
        if (rnd.nextInt(3) == 0) s = s.color(COLORS[rnd.nextInt(COLORS.length)]);
        for (TextDecoration d : TextDecoration.values()) {
            int r = rnd.nextInt(6);
            if (r == 0) s = s.decoration(d, true);
            else if (r == 1) s = s.decoration(d, false);
        }
        if (events) {
            if (rnd.nextInt(8) == 0) s = s.clickEvent(ClickEvent.runCommand("/say " + WORDS[rnd.nextInt(WORDS.length)]));
            if (rnd.nextInt(8) == 0) s = s.hoverEvent(HoverEvent.showText(Text.text("tip " + WORDS[rnd.nextInt(WORDS.length)], COLORS[rnd.nextInt(COLORS.length)])));
            if (rnd.nextInt(10) == 0) s = s.insertion("ins:" + rnd.nextInt(3));
            if (rnd.nextInt(10) == 0) s = s.font("minecraft:uniform");
        }
        return s;
    }
}
