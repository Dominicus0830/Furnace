package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;

/** 호환 모듈 전용: NMS 태그 shim ↔ Furnace 공개 NBT 값(깊은 사본). 수·문자열은 박싱 값, 배열은 사본, 리스트·묶음은 새 객체. */
public final class NbtBridge {

    private NbtBridge() {}

    public static Object toFurnace(Tag t) {
        return switch (t) {
            case ByteTag b -> b.getAsByte();
            case ShortTag s -> s.getAsShort();
            case IntTag i -> i.getAsInt();
            case LongTag l -> l.getAsLong();
            case FloatTag f -> f.getAsFloat();
            case DoubleTag d -> d.getAsDouble();
            case StringTag s -> s.getAsString();
            case ByteArrayTag a -> a.getAsByteArray().clone();
            case IntArrayTag a -> a.getAsIntArray().clone();
            case LongArrayTag a -> a.getAsLongArray().clone();
            case ListTag l -> {
                NbtList out = new NbtList();
                for (Tag e : l) out.add(toFurnace(e));
                yield out;
            }
            case CompoundTag c -> {
                NbtCompound out = new NbtCompound();
                for (String k : c.getAllKeys()) out.put(k, toFurnace(c.get(k)));
                yield out;
            }
            default -> throw new IllegalArgumentException("Furnace NBT 값으로 바꿀 수 없는 태그: " + t.getClass().getName());
        };
    }

    public static Tag fromFurnace(Object v) {
        return switch (v) {
            case Byte b -> ByteTag.valueOf(b);
            case Short s -> ShortTag.valueOf(s);
            case Integer i -> IntTag.valueOf(i);
            case Long l -> LongTag.valueOf(l);
            case Float f -> FloatTag.valueOf(f);
            case Double d -> DoubleTag.valueOf(d);
            case String s -> StringTag.valueOf(s);
            case byte[] a -> new ByteArrayTag(a.clone());
            case int[] a -> new IntArrayTag(a.clone());
            case long[] a -> new LongArrayTag(a.clone());
            case NbtList l -> {
                List<Tag> items = new ArrayList<>(l.size());
                for (Object e : l.values()) items.add(fromFurnace(e));
                yield new ListTag(items, items.isEmpty() ? Tag.TAG_END : items.get(0).getId());
            }
            case NbtCompound c -> {
                Map<String, Tag> m = new HashMap<>();
                for (String k : c.keys()) m.put(k, fromFurnace(c.get(k)));
                yield new CompoundTag(m);
            }
            default -> throw new IllegalArgumentException("NBT 값이 아니다: " + v.getClass().getName());
        };
    }
}
