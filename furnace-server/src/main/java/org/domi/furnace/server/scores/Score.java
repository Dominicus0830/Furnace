// 바닐라 Score — 한 주인·한 목표의 점수. 새 점수는 잠겨(locked = true) 있다(trigger 가 enable 로 연다).
// 저장: {Score:int, Locked:byte, display?:텍스트 JSON 문자열, format?:숫자 형식}.
package org.domi.furnace.server.scores;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class Score {

    int value;
    boolean locked = true;
    Text display;
    NumberFormat numberFormat;

    public int value() { return value; }

    public boolean isLocked() { return locked; }

    public Text display() { return display; }

    public NumberFormat numberFormat() { return numberFormat; }

    /** 바닐라 ReadOnlyScoreInfo.formatValue. */
    public Text formatValue(NumberFormat fallback) {
        NumberFormat f = numberFormat != null ? numberFormat : fallback;
        return f.format(value);
    }

    NbtCompound write() {
        NbtCompound t = new NbtCompound();
        t.putInt("Score", value);
        t.putBoolean("Locked", locked);
        if (display != null) t.putString("display", display.toJson());
        if (numberFormat != null) t.putCompound("format", NumberFormat.save(numberFormat));
        return t;
    }

    static Score read(NbtCompound t) {
        Score s = new Score();
        s.value = t.getIntOr0("Score");
        s.locked = t.getBoolean("Locked");
        if (t.tags().get("display") instanceof String json) {
            try {
                s.display = JsonTextSerializer.json().deserialize(json);
            } catch (RuntimeException e) {
                s.display = null;
            }
        }
        if (t.tags().get("format") instanceof NbtCompound f) s.numberFormat = NumberFormat.load(f);
        return s;
    }
}
