package org.bukkit.craftbukkit.block.sign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.DyeColor;
import org.bukkit.block.sign.SignSide;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;

/**
 * Paper CraftSignSide: 표지판 블록 엔티티 NBT 의 front_text·back_text(바닐라 SignText — messages 네 줄 JSON, color, has_glowing_text,
 * 있으면 filtered_messages) 위의 한 면. 줄은 처음 읽을 때 풀어 두고, 바뀐 줄만 {@link #applyTo} 가 적는다(Paper 와 같다 —
 * 안 고친 줄의 JSON 서식이 레거시 왕복으로 깎이지 않게). 레거시 변환은 Adventure legacySection 과 같은 Furnace LegacyTextSerializer.
 */
public final class CraftSignSide implements SignSide {

    private static final int LINES = 4;

    private final NbtCompound owner;
    private final String key;
    private List<Text> lines;
    private List<Text> originalLines;

    /** owner: 블록 엔티티 NBT 사본, key: "front_text" 또는 "back_text". */
    public CraftSignSide(NbtCompound owner, String key) {
        this.owner = owner;
        this.key = key;
    }

    /** 이 면의 SignText 칸 — 없으면 바닐라 기본값(빈 네 줄·검정·빛 없음)으로 만든다. */
    private NbtCompound text() {
        if (!(owner.get(key) instanceof NbtCompound t)) {
            NbtCompound t2 = new NbtCompound();
            NbtList messages = new NbtList();
            for (int i = 0; i < LINES; i++) messages.add(JsonTextSerializer.json().serialize(Text.empty()));
            t2.putList("messages", messages);
            t2.putString("color", "black");
            t2.putBoolean("has_glowing_text", false);
            owner.putCompound(key, t2);
            return t2;
        }
        return t;
    }

    private void loadLines() {
        if (lines != null) return;
        NbtList messages = owner.get(key) instanceof NbtCompound t ? t.getList("messages") : new NbtList();
        lines = new ArrayList<>(LINES);
        for (int i = 0; i < LINES; i++) {
            Text line;
            try {
                line = i < messages.size() ? JsonTextSerializer.json().deserialize(messages.getString(i)) : Text.empty();
            } catch (RuntimeException badJson) {
                line = Text.empty();   // 바닐라 SignText 코덱도 못 읽는 줄은 빈 줄
            }
            lines.add(line);
        }
        originalLines = new ArrayList<>(lines);
    }

    @Override
    public String[] getLines() {
        loadLines();
        return lines.stream().map(LegacyTextSerializer.section()::serialize).toArray(String[]::new);
    }

    @Override
    public String getLine(int index) {
        loadLines();
        return LegacyTextSerializer.section().serialize(lines.get(index));
    }

    @Override
    public void setLine(int index, String line) {
        loadLines();
        lines.set(index, line != null ? LegacyTextSerializer.section().deserialize(line) : Text.empty());
    }

    @Override
    public boolean isGlowingText() {
        return owner.get(key) instanceof NbtCompound t && t.getBoolean("has_glowing_text");
    }

    @Override
    public void setGlowingText(boolean glowing) { text().putBoolean("has_glowing_text", glowing); }

    @Override
    public DyeColor getColor() {
        String c = owner.get(key) instanceof NbtCompound t && t.contains("color") ? t.getString("color") : "black";
        try {
            return DyeColor.valueOf(c.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return DyeColor.BLACK;   // 바닐라 DyeColor.byName 기본값
        }
    }

    @Override
    public void setColor(DyeColor color) { text().putString("color", color.name().toLowerCase(Locale.ROOT)); }

    /** 바뀐 줄을 NBT 에 적는다(바닐라 SignText.setMessage — 걸러진 줄도 같은 값으로). */
    public void applyTo() {
        if (lines == null) return;
        for (int i = 0; i < LINES; i++) {
            Text now = lines.get(i);
            if (now.equals(originalLines.get(i))) continue;
            NbtCompound t = text();
            String json = JsonTextSerializer.json().serialize(now);
            NbtList messages = t.getList("messages");
            while (messages.size() < LINES) messages.add(JsonTextSerializer.json().serialize(Text.empty()));
            messages.set(i, json);
            t.putList("messages", messages);
            if (t.get("filtered_messages") instanceof NbtList filtered && filtered.size() == LINES) {
                filtered.set(i, json);
                if (filtered.values().equals(messages.values())) t.remove("filtered_messages");   // 바닐라는 같으면 적지 않는다
            }
        }
        originalLines = new ArrayList<>(lines);
    }
}
