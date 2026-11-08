package net.minecraft.nbt;

/**
 * SNBT 읽기(Mojang 이름 shim, H7b). 문법은 바닐라 TagParser 1.21.1 과 같다(Furnace {@link org.domi.furnace.api.nbt.Snbt}).
 * 바닐라는 틀린 글에 brigadier {@code CommandSyntaxException}(검사 예외)을 던지지만 이 서버는 brigadier 를 싣지 않는다 —
 * 대신 {@link org.domi.furnace.compat.nms.UnsupportedNmsCall} 에 읽기 오류를 담아 던진다(조용히 빈 값을 주지 않는다).
 */
public final class TagParser {

    private TagParser() {}

    public static CompoundTag parseTag(String snbt) {
        try {
            return CompoundTag.fromFurnace(org.domi.furnace.api.nbt.NbtCompound.parse(snbt));
        } catch (org.domi.furnace.api.nbt.SnbtParseException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("TagParser#parseTag 의 CommandSyntaxException",
                    "틀린 SNBT 다(" + e.getMessage() + ") — 바닐라는 brigadier CommandSyntaxException 을 던지지만 이 서버에는 brigadier 가 없다");
        }
    }
}
