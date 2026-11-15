package org.domi.furnace.compat.nms;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.nbt.NbtType;

/**
 * 패킷 안 NBT(1.20.2+ 네트워크 형식: 이름 없는 뿌리 태그) 읽기·쓰기(T-182). 바닐라 FriendlyByteBuf#readNbt·writeNbt 와 같은 바이트.
 * 깊이 상한은 바닐라 NbtAccounter 와 같은 512, 크기 상한(2 MiB)은 바닐라 패킷 상한에 맡긴다.
 */
public final class NbtWire {

    private NbtWire() {}

    private static final int MAX_DEPTH = 512;

    /** 뿌리 compound(태그 0 = 없음 → null). 뿌리가 compound 가 아니면 IllegalArgumentException(바닐라 readNbt 도 compound 만). */
    public static NbtCompound read(ByteBuf buf) {
        try {
            // 첫 태그 바이트는 버퍼에서 바로 읽는다: ProtocolLib ZeroBuffer(읽을 바이트 0, readByte 는 늘 0)로 빈 자료를 만들 때
            // 스트림으로 읽으면 EOF 가 난다(PR #399 검토 R1)
            int t = buf.readByte();
            if (t == 0) return null;
            DataInput in = new DataInputStream(new ByteBufInputStream(buf));
            if (t != 10) throw new IllegalArgumentException("the packet NBT root is not a compound (10): " + t);
            return compound(in, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 뿌리 compound 쓰기(null 이면 태그 0). */
    public static void write(ByteBuf buf, NbtCompound c) {
        try {
            DataOutput out = new DataOutputStream(new ByteBufOutputStream(buf));
            if (c == null) {
                out.writeByte(0);
                return;
            }
            out.writeByte(10);
            writeCompound(out, c);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static NbtCompound compound(DataInput in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("NBT depth exceeds " + MAX_DEPTH);
        NbtCompound c = new NbtCompound();
        while (true) {
            int t = in.readByte();
            if (t == 0) return c;
            String key = in.readUTF();
            c.put(key, payload(in, t, depth + 1));
        }
    }

    private static Object payload(DataInput in, int t, int depth) throws IOException {
        return switch (t) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] a = new byte[length(in)];
                in.readFully(a);
                yield a;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                if (depth > MAX_DEPTH) throw new IllegalArgumentException("NBT depth exceeds " + MAX_DEPTH);
                int et = in.readByte();
                int n = in.readInt();
                if (et == 0 && n > 0) throw new IllegalArgumentException("NBT list has element type END but length " + n);
                NbtList l = new NbtList();
                for (int i = 0; i < Math.max(n, 0); i++) l.add(payload(in, et, depth + 1));
                yield l;
            }
            case 10 -> compound(in, depth);
            case 11 -> {
                int[] a = new int[length(in)];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                yield a;
            }
            case 12 -> {
                long[] a = new long[length(in)];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                yield a;
            }
            default -> throw new IllegalArgumentException("unknown NBT tag type: " + t);
        };
    }

    private static int length(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0) throw new IllegalArgumentException("negative NBT array length: " + n);
        return n;
    }

    private static void writeCompound(DataOutput out, NbtCompound c) throws IOException {
        for (String k : c.keys()) {
            Object v = c.get(k);
            out.writeByte(NbtType.of(v).id());
            out.writeUTF(k);
            writePayload(out, v);
        }
        out.writeByte(0);
    }

    private static void writePayload(DataOutput out, Object v) throws IOException {
        switch (v) {
            case Byte b -> out.writeByte(b);
            case Short s -> out.writeShort(s);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case Float f -> out.writeFloat(f);
            case Double d -> out.writeDouble(d);
            case byte[] a -> {
                out.writeInt(a.length);
                out.write(a);
            }
            case String s -> out.writeUTF(s);
            case NbtList l -> {
                out.writeByte(l.isEmpty() ? 0 : NbtType.of(l.get(0)).id());
                out.writeInt(l.size());
                for (int i = 0; i < l.size(); i++) writePayload(out, l.get(i));
            }
            case NbtCompound c -> writeCompound(out, c);
            case int[] a -> {
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
            }
            default -> throw new IllegalArgumentException("not an NBT value: " + v);
        }
    }
}
