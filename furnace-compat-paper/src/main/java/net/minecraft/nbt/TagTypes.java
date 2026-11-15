package net.minecraft.nbt;

/** 태그 번호 → 종류(Mojang 이름 shim, 바닐라 1.21.1 과 같은 표·범위 밖 처리). */
public class TagTypes {

    private static final TagType<?>[] TYPES = {EndTag.TYPE, ByteTag.TYPE, ShortTag.TYPE, IntTag.TYPE, LongTag.TYPE, FloatTag.TYPE, DoubleTag.TYPE,
            ByteArrayTag.TYPE, StringTag.TYPE, ListTag.TYPE, CompoundTag.TYPE, IntArrayTag.TYPE, LongArrayTag.TYPE};

    public TagTypes() {}

    public static TagType<?> getType(int id) {
        return id >= 0 && id < TYPES.length ? TYPES[id] : TagType.createInvalid(id);
    }
}
