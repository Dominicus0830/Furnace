// 바닐라 NumberFormat(blank·styled·fixed) — 점수 숫자를 어떻게 보일지.
// 네트워크: number_format_type 레지스트리 번호(VarInt: blank 0, styled 1, fixed 2) + 내용(styled 는 스타일 NBT, fixed 는 텍스트 NBT).
// 저장: {type:"minecraft:blank"} / {type:"minecraft:styled", <스타일 필드>} / {type:"minecraft:fixed", value:<텍스트>}.
package org.domi.furnace.server.scores;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public sealed interface NumberFormat {

    /** 바닐라 format(점수). */
    Text format(int value);

    /** 레지스트리 번호(1.21.1 number_format_type). */
    int typeId();

    String typeName();

    void writeContents(ByteBuf buf);

    void saveContents(NbtCompound tag);

    Blank BLANK = new Blank();

    /** 바닐라 StyledFormat.NO_STYLE 등은 명령에서 쓰지 않으므로 두지 않는다. */
    record Blank() implements NumberFormat {
        public Text format(int value) { return Text.empty(); }
        public int typeId() { return 0; }
        public String typeName() { return "minecraft:blank"; }
        public void writeContents(ByteBuf buf) { }
        public void saveContents(NbtCompound tag) { }
    }

    record Styled(Style style) implements NumberFormat {
        public Text format(int value) { return Text.text(Integer.toString(value)).mergeStyle(style); }
        public int typeId() { return 1; }
        public String typeName() { return "minecraft:styled"; }
        public void writeContents(ByteBuf buf) { buf.writeBytes(style.networkNbt()); }
        public void saveContents(NbtCompound tag) { tag.tags().putAll(TextTags.styleToTag(style).tags()); }
    }

    record Fixed(Text value) implements NumberFormat {
        public Text format(int v) { return value; }
        public int typeId() { return 2; }
        public String typeName() { return "minecraft:fixed"; }
        public void writeContents(ByteBuf buf) { buf.writeBytes(value.networkNbt()); }
        public void saveContents(NbtCompound tag) { tag.put("value", TextTags.textToTag(value)); }
    }

    /** 바닐라 NumberFormatTypes.STREAM_CODEC. */
    static void write(ByteBuf buf, NumberFormat f) {
        Types.writeVarInt(buf, f.typeId());
        f.writeContents(buf);
    }

    /** 바닐라 OPTIONAL_STREAM_CODEC(bool + 값). */
    static void writeOptional(ByteBuf buf, NumberFormat f) {
        buf.writeBoolean(f != null);
        if (f != null) write(buf, f);
    }

    static NbtCompound save(NumberFormat f) {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", f.typeName());
        f.saveContents(tag);
        return tag;
    }

    /** 저장 형식에서(모르는 종류·깨진 내용은 null — 바닐라 parse 실패 결과와 같다). */
    static NumberFormat load(NbtCompound tag) {
        if (tag == null || !(tag.tags().get("type") instanceof String type)) return null;
        String t = type.contains(":") ? type : "minecraft:" + type;
        return switch (t) {
            case "minecraft:blank" -> BLANK;
            case "minecraft:styled" -> {
                NbtCompound rest = tag.copy();
                rest.tags().remove("type");
                Style s = TextTags.tagToStyle(rest);
                yield s == null ? null : new Styled(s);
            }
            case "minecraft:fixed" -> {
                Object v = tag.tags().get("value");
                Text text = v == null ? null : TextTags.tagToText(v);
                yield text == null ? null : new Fixed(text);
            }
            default -> null;
        };
    }
}
