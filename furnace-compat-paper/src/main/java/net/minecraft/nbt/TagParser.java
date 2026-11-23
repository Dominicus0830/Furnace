package net.minecraft.nbt;

/**
 * SNBT 읽기(Mojang 이름 shim, H7b). 문법은 바닐라 TagParser 1.21.1 과 같다(Furnace {@link org.domi.furnace.api.nbt.Snbt}).
 * 바닐라는 틀린 글에 brigadier {@code CommandSyntaxException}(검사 예외)을 던지지만 이 서버는 brigadier 를 싣지 않는다 —
 * 대신 {@link org.domi.furnace.compat.nms.UnsupportedNmsCall} 에 읽기 오류를 담아 던진다(조용히 빈 값을 주지 않는다).
 */
public final class TagParser {

    private final com.mojang.brigadier.StringReader reader;

    private TagParser() { this.reader = null; }

    /** 바닐라 {@code new TagParser(StringReader)}(NMS shim 5e-3c — 블록 상태 글의 {…} 부분). Brigadier 가 붙은 서버에서만 부를 수 있다. */
    public TagParser(com.mojang.brigadier.StringReader reader) { this.reader = reader; }

    /**
     * 바닐라 readStruct: 지금 자리부터 공백을 건너 묶음 하나를 읽고 그 뒤로 커서를 옮긴다. 틀리면 Brigadier 명령 오류 — 문구는 바닐라 en_us 와 같고(예
     * "Expected key at position 7: …<--[HERE]") 위치는 Furnace SNBT 읽기가 멈춘 글자다.
     */
    public CompoundTag readStruct() throws com.mojang.brigadier.exceptions.CommandSyntaxException { return Lib.readStruct(reader); }

    public static CompoundTag parseTag(String snbt) {
        try {
            return CompoundTag.fromFurnace(org.domi.furnace.api.nbt.NbtCompound.parse(snbt));
        } catch (org.domi.furnace.api.nbt.SnbtParseException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CommandSyntaxException of TagParser#parseTag",
                    "Invalid SNBT (" + e.getMessage() + ") - vanilla throws a brigadier CommandSyntaxException, but this server has no brigadier");
        }
    }

    /** {@link #readStruct} 의 몸 — Brigadier 예외를 던지는 코드를 떼어 둔다(이름 {@code $Lib} — CompatLinkTest 규칙). Brigadier 없는 서버에서도 {@link #parseTag} 가 검증·로드된다. */
    private static final class Lib {

        /** 바닐라 오류 문구(en_us)를 그대로 싣는다 — Furnace SNBT 읽기의 문구가 바닐라 TagParser·Brigadier 글 읽기 문구와 같다. */
        private static final com.mojang.brigadier.exceptions.DynamicCommandExceptionType ERROR =
                new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(m -> new com.mojang.brigadier.LiteralMessage(String.valueOf(m)));

        private Lib() {}

        static CompoundTag readStruct(com.mojang.brigadier.StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            try {
                var p = org.domi.furnace.api.nbt.Snbt.readCompound(reader.getString(), reader.getCursor());
                reader.setCursor(p.end());
                return CompoundTag.fromFurnace(p.value());
            } catch (org.domi.furnace.api.nbt.SnbtParseException e) {
                reader.setCursor(Math.min(e.position(), reader.getTotalLength()));
                throw ERROR.createWithContext(reader, e.reason());
            }
        }
    }
}
