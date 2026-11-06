// 바닐라 SignText: 표지판 한 면 — 글 4줄(messages)·걸러진 글 4줄(filtered_messages)·염료 색·발광. 불변, 바꾸면 새 객체.
// 저장 꼴: messages(텍스트 JSON 문자열 4개), filtered_messages(messages 와 다를 때만), color(기본 black), has_glowing_text.
package org.domi.furnace.server.block.entity;

import java.util.Arrays;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class SignText {

    public static final int LINES = 4;
    /** DyeColor 이름(id 순서). */
    public static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"};

    private final Text[] messages;
    private final Text[] filtered;
    private final String color;
    private final boolean glowing;

    public SignText() { this(empty(), empty(), "black", false); }

    public SignText(Text[] messages, Text[] filtered, String color, boolean glowing) {
        this.messages = messages;
        this.filtered = filtered;
        this.color = color;
        this.glowing = glowing;
    }

    private static Text[] empty() { return new Text[] {Text.empty(), Text.empty(), Text.empty(), Text.empty()}; }

    public boolean hasGlowingText() { return glowing; }

    public SignText setHasGlowingText(boolean v) { return v == glowing ? this : new SignText(messages, filtered, color, v); }

    public String color() { return color; }

    public SignText setColor(String c) { return c.equals(color) ? this : new SignText(messages, filtered, c, glowing); }

    public Text message(int i, boolean filtering) { return messages(filtering)[i]; }

    public SignText setMessage(int i, Text t) { return setMessage(i, t, t); }

    public SignText setMessage(int i, Text msg, Text filteredMsg) {
        Text[] m = Arrays.copyOf(messages, LINES), f = Arrays.copyOf(filtered, LINES);
        m[i] = msg;
        f[i] = filteredMsg;
        return new SignText(m, f, color, glowing);
    }

    public Text[] messages(boolean filtering) { return filtering ? filtered : messages; }

    /** hasMessage(player): 보이는 줄 가운데 평문이 비지 않은 것이 있는가. */
    public boolean hasMessage(boolean filtering) {
        for (Text t : messages(filtering)) if (!t.getString().isEmpty()) return true;
        return false;
    }

    /** hasAnyClickCommands: 줄의 맨 위 스타일에 run_command 클릭이 있는가. */
    public boolean hasAnyClickCommands(boolean filtering) {
        for (Text t : messages(filtering)) {
            ClickEvent e = t.clickEvent();
            if (e != null && e.action() == ClickEvent.Action.RUN_COMMAND) return true;
        }
        return false;
    }

    /** SignBlock.isSignEmpty 판정에 쓰는: 모든 줄이 빈 글이거나 글자 내용(PlainTextContents)인가. */
    public boolean allPlainText(boolean filtering) {
        for (Text t : messages(filtering)) if (!(t.equals(Text.empty()) || t.content() instanceof Content.Literal)) return false;
        return true;
    }

    private boolean filteredSame() {
        for (int i = 0; i < LINES; i++) if (!filtered[i].equals(messages[i])) return false;
        return true;
    }

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        t.putList("messages", lines(messages));
        if (!filteredSame()) t.putList("filtered_messages", lines(filtered));
        t.putString("color", color);
        t.putBoolean("has_glowing_text", glowing);
        return t;
    }

    private static NbtList lines(Text[] a) {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        for (Text t : a) l.add(FlatText.stable(t));
        return l;
    }

    /** 코덱 해석: messages 가 없거나 4줄이 아니면 실패(null — 바닐라 resultOrPartial 은 기존 면을 둔다). */
    public static SignText load(NbtCompound t) {
        Text[] m = parseLines(t.tags().get("messages"));
        if (m == null) return null;
        Text[] f = parseLines(t.tags().get("filtered_messages"));   // lenientOptional: 틀리면 없는 것으로
        if (f == null) f = Arrays.copyOf(m, LINES);
        String color = "black";
        if (t.tags().get("color") instanceof String c) {
            if (!Arrays.asList(COLORS).contains(c)) return null;
            color = c;
        }
        boolean glow = t.tags().get("has_glowing_text") instanceof Number n && n.byteValue() != 0;
        return new SignText(m, f, color, glow);
    }

    private static Text[] parseLines(Object o) {
        if (!(o instanceof NbtList l) || l.items().size() != LINES) return null;
        Text[] r = new Text[LINES];
        try {
            for (int i = 0; i < LINES; i++) {
                if (!(l.items().get(i) instanceof String s)) return null;
                r[i] = FlatText.parse(s);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return r;
    }
}
