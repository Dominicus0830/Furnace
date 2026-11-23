package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 바닐라 이진 NBT 읽기·쓰기(Mojang 이름, NMS shim 5e-3 — Axiom 이 블루프린트·블록 데이터에 쓴다). 동작은 Paper 1.21.1(javap·디컴파일 대조)과 같다:
 * 뿌리는 이름 있는 묶음이어야 하고("Root tag must be a named compound tag"), 끝 태그 뿌리는 0 한 바이트, 압축은 gzip, Paper 처럼 네트워크 버퍼
 * 스트림에서 읽으면 읽은 바이트도 한도에 센다. 파일 경로판·방문자({@code parse})는 아직 없다(없는 멤버 — 부르는 곳에서 링크 오류).
 */
public class NbtIo {

    public NbtIo() {}

    public static CompoundTag readCompressed(InputStream stream, NbtAccounter accounter) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            return read(in, accounter);
        }
    }

    public static void writeCompressed(CompoundTag nbt, OutputStream stream) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(stream)))) {
            writeUnnamedTagWithFallback(nbt, out);
        }
    }

    private static final java.nio.file.OpenOption[] FILE_WRITE_OPTIONS = {java.nio.file.StandardOpenOption.SYNC, java.nio.file.StandardOpenOption.WRITE,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING};

    /** 바닐라 {@code readCompressed(Path, accounter)}: 파일을 열어 gzip 으로 읽는다(없으면 NoSuchFileException). */
    public static CompoundTag readCompressed(java.nio.file.Path path, NbtAccounter accounter) throws IOException {
        try (InputStream file = java.nio.file.Files.newInputStream(path); InputStream buffered = new BufferedInputStream(file)) {
            return readCompressed(buffered, accounter);
        }
    }

    /** 바닐라 {@code writeCompressed(nbt, Path)}: SYNC·WRITE·CREATE·TRUNCATE_EXISTING 으로 열어 gzip 으로 쓴다. */
    public static void writeCompressed(CompoundTag nbt, java.nio.file.Path path) throws IOException {
        try (OutputStream file = java.nio.file.Files.newOutputStream(path, FILE_WRITE_OPTIONS); OutputStream buffered = new BufferedOutputStream(file)) {
            writeCompressed(nbt, buffered);
        }
    }

    /** 바닐라 {@code write(nbt, Path)}: 압축 없이 같은 방식으로 쓴다. */
    public static void write(CompoundTag nbt, java.nio.file.Path path) throws IOException {
        try (OutputStream file = java.nio.file.Files.newOutputStream(path, FILE_WRITE_OPTIONS); OutputStream buffered = new BufferedOutputStream(file);
             DataOutputStream out = new DataOutputStream(buffered)) {
            write(nbt, out);
        }
    }

    /** 바닐라 {@code read(Path)}: 파일이 없으면 null, 압축 없이 읽는다(한도 없음). */
    public static CompoundTag read(java.nio.file.Path path) throws IOException {
        if (!java.nio.file.Files.exists(path)) return null;
        try (InputStream file = java.nio.file.Files.newInputStream(path); DataInputStream in = new DataInputStream(file)) {
            return read(in, NbtAccounter.unlimitedHeap());
        }
    }

    public static CompoundTag read(DataInput input) throws IOException { return read(input, NbtAccounter.unlimitedHeap()); }

    public static CompoundTag read(DataInput input, NbtAccounter accounter) throws IOException {
        if (input instanceof io.netty.buffer.ByteBufInputStream in) input = new DataInputStream(new org.spigotmc.LimitStream(in, accounter));
        Tag tag = readUnnamedTag(input, accounter);
        if (tag instanceof CompoundTag c) return c;
        throw new IOException("Root tag must be a named compound tag");
    }

    public static void write(CompoundTag nbt, DataOutput output) throws IOException { writeUnnamedTagWithFallback(nbt, output); }

    public static Tag readAnyTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte id = input.readByte();
        return id == Tag.TAG_END ? EndTag.INSTANCE : readTagSafe(input, accounter, id);
    }

    public static void writeAnyTag(Tag nbt, DataOutput output) throws IOException {
        output.writeByte(nbt.getId());
        if (nbt.getId() != Tag.TAG_END) nbt.write(output);
    }

    public static void writeUnnamedTag(Tag nbt, DataOutput output) throws IOException {
        output.writeByte(nbt.getId());
        if (nbt.getId() != Tag.TAG_END) {
            output.writeUTF("");
            nbt.write(output);
        }
    }

    /** 바닐라: 너무 긴 문자열(UTF 65535 바이트 넘음)은 오류를 남기고 빈 문자열로 쓴다. */
    public static void writeUnnamedTagWithFallback(Tag nbt, DataOutput output) throws IOException {
        writeUnnamedTag(nbt, new StringFallback(output));
    }

    private static Tag readUnnamedTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte id = input.readByte();
        if (id == Tag.TAG_END) return EndTag.INSTANCE;
        input.skipBytes(input.readUnsignedShort());   // 뿌리 이름(바닐라 StringTag.skipString)
        return readTagSafe(input, accounter, id);
    }

    private static Tag readTagSafe(DataInput input, NbtAccounter accounter, byte id) {
        try {
            return TagTypes.getType(id).load(input, accounter);
        } catch (IOException e) {
            throw NbtBinary.reported(e);
        }
    }

    private static final class StringFallback implements DataOutput {
        private final DataOutput out;

        StringFallback(DataOutput out) { this.out = out; }

        @Override
        public void writeUTF(String s) throws IOException {
            try {
                out.writeUTF(s);
            } catch (UTFDataFormatException e) {
                org.slf4j.LoggerFactory.getLogger(NbtIo.class).error("Failed to write NBT String", e);
                out.writeUTF("");
            }
        }

        @Override public void write(int b) throws IOException { out.write(b); }
        @Override public void write(byte[] b) throws IOException { out.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
        @Override public void writeBoolean(boolean v) throws IOException { out.writeBoolean(v); }
        @Override public void writeByte(int v) throws IOException { out.writeByte(v); }
        @Override public void writeShort(int v) throws IOException { out.writeShort(v); }
        @Override public void writeChar(int v) throws IOException { out.writeChar(v); }
        @Override public void writeInt(int v) throws IOException { out.writeInt(v); }
        @Override public void writeLong(long v) throws IOException { out.writeLong(v); }
        @Override public void writeFloat(float v) throws IOException { out.writeFloat(v); }
        @Override public void writeDouble(double v) throws IOException { out.writeDouble(v); }
        @Override public void writeBytes(String s) throws IOException { out.writeBytes(s); }
        @Override public void writeChars(String s) throws IOException { out.writeChars(s); }
    }
}
