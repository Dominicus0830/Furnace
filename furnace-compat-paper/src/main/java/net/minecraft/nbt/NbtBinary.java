package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이 패키지 전용: 바닐라 1.21.1 이진 NBT 읽기·쓰기(NMS shim 5e-3 — Axiom 이 {@link NbtIo} 로 블루프린트·블록 데이터를 주고받는다).
 * Furnace 가 새로 쓴 구현이고, 바닐라와 같게 맞춘 것: 바이트 꼴, 태그마다 {@link NbtAccounter} 에 세는 바이트 수(각 태그의 {@code sizeInBytes} 와 같은 식,
 * 묶음 키는 같은 키가 처음일 때만 36), 깊이(리스트·묶음), 오류 문구, 묶음 안의 입출력 오류를 "Loading NBT data" 보고 예외로 감싸기.
 */
final class NbtBinary {

    private static final int MAX_ARRAY = 16777216;

    private NbtBinary() {}

    static Tag load(int id, DataInput in, NbtAccounter acc) throws IOException {
        switch (id) {
            case Tag.TAG_END -> { acc.accountBytes(8); return EndTag.INSTANCE; }
            case Tag.TAG_BYTE -> { acc.accountBytes(9); return ByteTag.valueOf(in.readByte()); }
            case Tag.TAG_SHORT -> { acc.accountBytes(10); return ShortTag.valueOf(in.readShort()); }
            case Tag.TAG_INT -> { acc.accountBytes(12); return IntTag.valueOf(in.readInt()); }
            case Tag.TAG_LONG -> { acc.accountBytes(16); return LongTag.valueOf(in.readLong()); }
            case Tag.TAG_FLOAT -> { acc.accountBytes(12); return FloatTag.valueOf(in.readFloat()); }
            case Tag.TAG_DOUBLE -> { acc.accountBytes(16); return DoubleTag.valueOf(in.readDouble()); }
            case Tag.TAG_BYTE_ARRAY -> {
                acc.accountBytes(24);
                int n = arrayLength(in);
                acc.accountBytes(1, n);
                byte[] a = new byte[n];
                in.readFully(a);
                return new ByteArrayTag(a);
            }
            case Tag.TAG_STRING -> {
                acc.accountBytes(36);
                String s = in.readUTF();
                acc.accountBytes(2, s.length());
                return StringTag.valueOf(s);
            }
            case Tag.TAG_LIST -> {
                acc.pushDepth();
                try {
                    acc.accountBytes(37);
                    byte type = in.readByte();
                    int n = in.readInt();
                    if (type == Tag.TAG_END && n > 0) throw new NbtFormatException("Missing type on ListTag");
                    acc.accountBytes(4, n);
                    if (n < 0) throw new IllegalArgumentException("initialArraySize cannot be negative but was: " + n);   // 바닐라 Guava 목록 만들기
                    List<Tag> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(load(type, in, acc));
                    return new ListTag(list, type);
                } finally {
                    acc.popDepth();
                }
            }
            case Tag.TAG_COMPOUND -> {
                acc.pushDepth();
                try {
                    acc.accountBytes(48);
                    Map<String, Tag> map = new HashMap<>();
                    byte type;
                    while ((type = in.readByte()) != Tag.TAG_END) {
                        String key = in.readUTF();
                        acc.accountBytes(28);
                        acc.accountBytes(2, key.length());
                        Tag value;
                        try {
                            value = load(type, in, acc);
                        } catch (IOException e) {
                            throw reported(e);
                        }
                        if (map.put(key, value) == null) acc.accountBytes(36);
                    }
                    return new CompoundTag(map);
                } finally {
                    acc.popDepth();
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                acc.accountBytes(24);
                int n = arrayLength(in);
                acc.accountBytes(4, n);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return new IntArrayTag(a);
            }
            case Tag.TAG_LONG_ARRAY -> {
                acc.accountBytes(24);
                int n = in.readInt();   // 바닐라도 long 배열은 길이 상한을 보지 않는다
                acc.accountBytes(8, n);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return new LongArrayTag(a);
            }
            default -> throw new IOException("Invalid tag id: " + id);
        }
    }

    private static int arrayLength(DataInput in) throws IOException {
        int n = in.readInt();
        if (n >= MAX_ARRAY) throw new IllegalArgumentException();   // Paper 패치 Preconditions.checkArgument(n < 16777216) — 문구 없음. 바닐라 1.21.1 에는 없다(호환 원칙대로 Paper 와 같게, T-235)
        return n;
    }

    /** 바닐라: 읽기 입출력 오류를 "Loading NBT data" 보고 예외로. */
    static ReportedNbtException reported(IOException e) {
        return new ReportedNbtException(net.minecraft.CrashReport.forThrowable(e, "Loading NBT data"));
    }

    /** 태그 몸통 쓰기(이름·번호 없이). 리스트·묶음은 각자 쓴다. */
    static void writePayload(Tag tag, DataOutput out) throws IOException {
        switch (tag) {
            case EndTag e -> { }
            case ByteTag b -> out.writeByte(b.getAsByte());
            case ShortTag s -> out.writeShort(s.getAsShort());
            case IntTag i -> out.writeInt(i.getAsInt());
            case LongTag l -> out.writeLong(l.getAsLong());
            case FloatTag f -> out.writeFloat(f.getAsFloat());
            case DoubleTag d -> out.writeDouble(d.getAsDouble());
            case StringTag s -> out.writeUTF(s.getAsString());
            case ByteArrayTag a -> {
                byte[] v = a.getAsByteArray();
                out.writeInt(v.length);
                out.write(v);
            }
            case IntArrayTag a -> {
                int[] v = a.getAsIntArray();
                out.writeInt(v.length);
                for (int x : v) out.writeInt(x);
            }
            case LongArrayTag a -> {
                long[] v = a.getAsLongArray();
                out.writeInt(v.length);
                for (long x : v) out.writeLong(x);
            }
            default -> throw new IllegalStateException("not a vanilla tag: " + tag.getClass().getName());
        }
    }
}
