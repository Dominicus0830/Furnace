package net.minecraft.nbt;

/** 태그 번호 → 종류(Mojang 이름 shim, 바닐라 1.21.1 과 같은 표·범위 밖 처리). */
public class TagTypes {

    private static final TagType<?>[] TYPES = {EndTag.TYPE, ByteTag.TYPE, ShortTag.TYPE, IntTag.TYPE, LongTag.TYPE, FloatTag.TYPE, DoubleTag.TYPE,
            ByteArrayTag.TYPE, StringTag.TYPE, ListTag.TYPE, CompoundTag.TYPE, IntArrayTag.TYPE, LongArrayTag.TYPE};

    public TagTypes() {}

    /** 이 패키지 전용: 표 안 종류의 번호. */
    static int idOf(TagType<?> type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i] == type) return i;
        throw new IllegalArgumentException("not a vanilla tag type: " + type.getName());
    }

    public static TagType<?> getType(int id) {
        return id >= 0 && id < TYPES.length ? TYPES[id] : TagType.createInvalid(id);
    }
}
