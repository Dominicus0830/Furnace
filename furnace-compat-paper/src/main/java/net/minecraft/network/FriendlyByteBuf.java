package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 패킷 바이트 버퍼 shim(Mojang 이름, N3 1단계). 바닐라처럼 netty {@link ByteBuf} 를 감싸 위임하고(읽기·쓰기 위치도 안쪽 버퍼와 같다),
 * 프로토콜의 값 형식(가변 길이 정수·문자열·UUID·블록 위치 등)을 읽고 쓰는 도우미를 더한다.
 * <p>
 * 위임 메서드(아래 "위임" 구간)는 netty 4.1.115 {@code ByteBuf} 의 추상 메서드를 기계적으로 만든 것이다
 * ({@code tools/compat/gen-friendly-bytebuf.py}). 도우미는 프로토콜 형식(가변 정수·UTF-8 문자열)을 새로 쓴 것이다.
 * 컴포넌트·NBT·아이템처럼 레지스트리나 서버 타입이 필요한 값은 패킷 바이트 변환(ProtocolLib 다리 3단계)과 함께 더한다.
 */
public class FriendlyByteBuf extends ByteBuf {

    private static final int MAX_VARINT_BYTES = 5;
    private static final int MAX_VARLONG_BYTES = 10;
    /** 바닐라 문자열 상한(UTF-16 코드 유닛 수). */
    private static final int MAX_STRING = 32767;

    private final ByteBuf source;

    public FriendlyByteBuf(ByteBuf source) { this.source = source; }

    // ---------------------------------------------------------------- 프로토콜 형식 도우미

    public static int getVarIntSize(int value) {
        for (int i = 1; i < MAX_VARINT_BYTES; i++) if ((value & -1 << i * 7) == 0) return i;
        return MAX_VARINT_BYTES;
    }

    public static int getVarLongSize(long value) {
        for (int i = 1; i < MAX_VARLONG_BYTES; i++) if ((value & -1L << i * 7) == 0L) return i;
        return MAX_VARLONG_BYTES;
    }

    public int readVarInt() {
        int result = 0;
        for (int shift = 0; shift < MAX_VARINT_BYTES * 7; shift += 7) {
            byte b = readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) != 128) return result;
        }
        throw new RuntimeException("VarInt too big");
    }

    public FriendlyByteBuf writeVarInt(int value) {
        while ((value & -128) != 0) {
            writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        writeByte(value);
        return this;
    }

    public long readVarLong() {
        long result = 0L;
        for (int shift = 0; shift < MAX_VARLONG_BYTES * 7; shift += 7) {
            byte b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) != 128) return result;
        }
        throw new RuntimeException("VarLong too big");
    }

    public FriendlyByteBuf writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            writeByte((int) (value & 0x7FL) | 0x80);
            value >>>= 7;
        }
        writeByte((int) value);
        return this;
    }

    public String readUtf() { return readUtf(MAX_STRING); }

    public String readUtf(int maxLength) {
        int bytes = readVarInt();
        if (bytes > maxLength * 3) throw new io.netty.handler.codec.DecoderException("The received encoded string buffer length is longer than maximum allowed (" + bytes + " > " + maxLength * 3 + ")");
        if (bytes < 0) throw new io.netty.handler.codec.DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        String s = toString(readerIndex(), bytes, StandardCharsets.UTF_8);
        readerIndex(readerIndex() + bytes);
        if (s.length() > maxLength) throw new io.netty.handler.codec.DecoderException("The received string length is longer than maximum allowed (" + s.length() + " > " + maxLength + ")");
        return s;
    }

    public FriendlyByteBuf writeUtf(String value) { return writeUtf(value, MAX_STRING); }

    public FriendlyByteBuf writeUtf(String value, int maxLength) {
        if (value.length() > maxLength) throw new io.netty.handler.codec.EncoderException("String too big (was " + value.length() + " characters, max " + maxLength + ")");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength * 3) throw new io.netty.handler.codec.EncoderException("String too big (was " + bytes.length + " bytes encoded, max " + maxLength * 3 + ")");
        writeVarInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    public UUID readUUID() { return new UUID(readLong(), readLong()); }

    public FriendlyByteBuf writeUUID(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    public byte[] readByteArray() { return readByteArray(readableBytes()); }

    public byte[] readByteArray(int maxLength) {
        int n = readVarInt();
        if (n > maxLength) throw new io.netty.handler.codec.DecoderException("ByteArray with size " + n + " is bigger than allowed " + maxLength);
        byte[] out = new byte[n];
        readBytes(out);
        return out;
    }

    public FriendlyByteBuf writeByteArray(byte[] value) {
        writeVarInt(value.length);
        writeBytes(value);
        return this;
    }

    public int[] readVarIntArray() {
        int n = readVarInt();
        if (n > readableBytes()) throw new io.netty.handler.codec.DecoderException("VarIntArray with size " + n + " is bigger than allowed " + readableBytes());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = readVarInt();
        return out;
    }

    public FriendlyByteBuf writeVarIntArray(int[] value) {
        writeVarInt(value.length);
        for (int v : value) writeVarInt(v);
        return this;
    }

    public long[] readLongArray() {
        int n = readVarInt();
        if (n > readableBytes() / 8) throw new io.netty.handler.codec.DecoderException("LongArray with size " + n + " is bigger than allowed " + readableBytes() / 8);
        long[] out = new long[n];
        for (int i = 0; i < n; i++) out[i] = readLong();
        return out;
    }

    public FriendlyByteBuf writeLongArray(long[] value) {
        writeVarInt(value.length);
        for (long v : value) writeLong(v);
        return this;
    }

    public BlockPos readBlockPos() { return BlockPos.of(readLong()); }

    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        writeLong(pos.asLong());
        return this;
    }

    public ResourceLocation readResourceLocation() { return ResourceLocation.parse(readUtf(MAX_STRING)); }

    public FriendlyByteBuf writeResourceLocation(ResourceLocation id) {
        writeUtf(id.toString());
        return this;
    }

    public <T extends Enum<T>> T readEnum(Class<T> type) { return type.getEnumConstants()[readVarInt()]; }

    public FriendlyByteBuf writeEnum(Enum<?> value) { return writeVarInt(value.ordinal()); }

    // ---------------------------------------------------------------- 위임 (tools/compat/gen-friendly-bytebuf.py)
    // ByteBuf 가 선언하지 않고 ReferenceCounted 에서 물려받는 것(생성기가 못 본다)
    @Override public int refCnt() { return source.refCnt(); }
    @Override public boolean release() { return source.release(); }
    @Override public boolean release(int p0) { return source.release(p0); }


    @Override public int capacity() { return source.capacity(); }
    @Override public io.netty.buffer.ByteBuf capacity(int p0) { source.capacity(p0); return this; }
    @Override public int maxCapacity() { return source.maxCapacity(); }
    @Override public io.netty.buffer.ByteBufAllocator alloc() { return source.alloc(); }
    @Override public java.nio.ByteOrder order() { return source.order(); }
    @Override public io.netty.buffer.ByteBuf order(java.nio.ByteOrder p0) { return source.order(p0); }
    @Override public io.netty.buffer.ByteBuf unwrap() { return source.unwrap(); }
    @Override public boolean isDirect() { return source.isDirect(); }
    @Override public boolean isReadOnly() { return source.isReadOnly(); }
    @Override public io.netty.buffer.ByteBuf asReadOnly() { return source.asReadOnly(); }
    @Override public int readerIndex() { return source.readerIndex(); }
    @Override public io.netty.buffer.ByteBuf readerIndex(int p0) { source.readerIndex(p0); return this; }
    @Override public int writerIndex() { return source.writerIndex(); }
    @Override public io.netty.buffer.ByteBuf writerIndex(int p0) { source.writerIndex(p0); return this; }
    @Override public io.netty.buffer.ByteBuf setIndex(int p0, int p1) { source.setIndex(p0, p1); return this; }
    @Override public int readableBytes() { return source.readableBytes(); }
    @Override public int writableBytes() { return source.writableBytes(); }
    @Override public int maxWritableBytes() { return source.maxWritableBytes(); }
    @Override public boolean isReadable() { return source.isReadable(); }
    @Override public boolean isReadable(int p0) { return source.isReadable(p0); }
    @Override public boolean isWritable() { return source.isWritable(); }
    @Override public boolean isWritable(int p0) { return source.isWritable(p0); }
    @Override public io.netty.buffer.ByteBuf clear() { source.clear(); return this; }
    @Override public io.netty.buffer.ByteBuf markReaderIndex() { source.markReaderIndex(); return this; }
    @Override public io.netty.buffer.ByteBuf resetReaderIndex() { source.resetReaderIndex(); return this; }
    @Override public io.netty.buffer.ByteBuf markWriterIndex() { source.markWriterIndex(); return this; }
    @Override public io.netty.buffer.ByteBuf resetWriterIndex() { source.resetWriterIndex(); return this; }
    @Override public io.netty.buffer.ByteBuf discardReadBytes() { source.discardReadBytes(); return this; }
    @Override public io.netty.buffer.ByteBuf discardSomeReadBytes() { source.discardSomeReadBytes(); return this; }
    @Override public io.netty.buffer.ByteBuf ensureWritable(int p0) { source.ensureWritable(p0); return this; }
    @Override public int ensureWritable(int p0, boolean p1) { return source.ensureWritable(p0, p1); }
    @Override public boolean getBoolean(int p0) { return source.getBoolean(p0); }
    @Override public byte getByte(int p0) { return source.getByte(p0); }
    @Override public short getUnsignedByte(int p0) { return source.getUnsignedByte(p0); }
    @Override public short getShort(int p0) { return source.getShort(p0); }
    @Override public short getShortLE(int p0) { return source.getShortLE(p0); }
    @Override public int getUnsignedShort(int p0) { return source.getUnsignedShort(p0); }
    @Override public int getUnsignedShortLE(int p0) { return source.getUnsignedShortLE(p0); }
    @Override public int getMedium(int p0) { return source.getMedium(p0); }
    @Override public int getMediumLE(int p0) { return source.getMediumLE(p0); }
    @Override public int getUnsignedMedium(int p0) { return source.getUnsignedMedium(p0); }
    @Override public int getUnsignedMediumLE(int p0) { return source.getUnsignedMediumLE(p0); }
    @Override public int getInt(int p0) { return source.getInt(p0); }
    @Override public int getIntLE(int p0) { return source.getIntLE(p0); }
    @Override public long getUnsignedInt(int p0) { return source.getUnsignedInt(p0); }
    @Override public long getUnsignedIntLE(int p0) { return source.getUnsignedIntLE(p0); }
    @Override public long getLong(int p0) { return source.getLong(p0); }
    @Override public long getLongLE(int p0) { return source.getLongLE(p0); }
    @Override public char getChar(int p0) { return source.getChar(p0); }
    @Override public float getFloat(int p0) { return source.getFloat(p0); }
    @Override public double getDouble(int p0) { return source.getDouble(p0); }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, io.netty.buffer.ByteBuf p1) { source.getBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, io.netty.buffer.ByteBuf p1, int p2) { source.getBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, io.netty.buffer.ByteBuf p1, int p2, int p3) { source.getBytes(p0, p1, p2, p3); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, byte[] p1) { source.getBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, byte[] p1, int p2, int p3) { source.getBytes(p0, p1, p2, p3); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, java.nio.ByteBuffer p1) { source.getBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf getBytes(int p0, java.io.OutputStream p1, int p2) throws java.io.IOException { source.getBytes(p0, p1, p2); return this; }
    @Override public int getBytes(int p0, java.nio.channels.GatheringByteChannel p1, int p2) throws java.io.IOException { return source.getBytes(p0, p1, p2); }
    @Override public int getBytes(int p0, java.nio.channels.FileChannel p1, long p2, int p3) throws java.io.IOException { return source.getBytes(p0, p1, p2, p3); }
    @Override public java.lang.CharSequence getCharSequence(int p0, int p1, java.nio.charset.Charset p2) { return source.getCharSequence(p0, p1, p2); }
    @Override public io.netty.buffer.ByteBuf setBoolean(int p0, boolean p1) { source.setBoolean(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setByte(int p0, int p1) { source.setByte(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setShort(int p0, int p1) { source.setShort(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setShortLE(int p0, int p1) { source.setShortLE(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setMedium(int p0, int p1) { source.setMedium(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setMediumLE(int p0, int p1) { source.setMediumLE(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setInt(int p0, int p1) { source.setInt(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setIntLE(int p0, int p1) { source.setIntLE(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setLong(int p0, long p1) { source.setLong(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setLongLE(int p0, long p1) { source.setLongLE(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setChar(int p0, int p1) { source.setChar(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setFloat(int p0, float p1) { source.setFloat(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setDouble(int p0, double p1) { source.setDouble(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, io.netty.buffer.ByteBuf p1) { source.setBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, io.netty.buffer.ByteBuf p1, int p2) { source.setBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, io.netty.buffer.ByteBuf p1, int p2, int p3) { source.setBytes(p0, p1, p2, p3); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, byte[] p1) { source.setBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, byte[] p1, int p2, int p3) { source.setBytes(p0, p1, p2, p3); return this; }
    @Override public io.netty.buffer.ByteBuf setBytes(int p0, java.nio.ByteBuffer p1) { source.setBytes(p0, p1); return this; }
    @Override public int setBytes(int p0, java.io.InputStream p1, int p2) throws java.io.IOException { return source.setBytes(p0, p1, p2); }
    @Override public int setBytes(int p0, java.nio.channels.ScatteringByteChannel p1, int p2) throws java.io.IOException { return source.setBytes(p0, p1, p2); }
    @Override public int setBytes(int p0, java.nio.channels.FileChannel p1, long p2, int p3) throws java.io.IOException { return source.setBytes(p0, p1, p2, p3); }
    @Override public io.netty.buffer.ByteBuf setZero(int p0, int p1) { source.setZero(p0, p1); return this; }
    @Override public int setCharSequence(int p0, java.lang.CharSequence p1, java.nio.charset.Charset p2) { return source.setCharSequence(p0, p1, p2); }
    @Override public boolean readBoolean() { return source.readBoolean(); }
    @Override public byte readByte() { return source.readByte(); }
    @Override public short readUnsignedByte() { return source.readUnsignedByte(); }
    @Override public short readShort() { return source.readShort(); }
    @Override public short readShortLE() { return source.readShortLE(); }
    @Override public int readUnsignedShort() { return source.readUnsignedShort(); }
    @Override public int readUnsignedShortLE() { return source.readUnsignedShortLE(); }
    @Override public int readMedium() { return source.readMedium(); }
    @Override public int readMediumLE() { return source.readMediumLE(); }
    @Override public int readUnsignedMedium() { return source.readUnsignedMedium(); }
    @Override public int readUnsignedMediumLE() { return source.readUnsignedMediumLE(); }
    @Override public int readInt() { return source.readInt(); }
    @Override public int readIntLE() { return source.readIntLE(); }
    @Override public long readUnsignedInt() { return source.readUnsignedInt(); }
    @Override public long readUnsignedIntLE() { return source.readUnsignedIntLE(); }
    @Override public long readLong() { return source.readLong(); }
    @Override public long readLongLE() { return source.readLongLE(); }
    @Override public char readChar() { return source.readChar(); }
    @Override public float readFloat() { return source.readFloat(); }
    @Override public double readDouble() { return source.readDouble(); }
    @Override public io.netty.buffer.ByteBuf readBytes(int p0) { source.readBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf readSlice(int p0) { return new FriendlyByteBuf(source.readSlice(p0)); }
    @Override public io.netty.buffer.ByteBuf readRetainedSlice(int p0) { return new FriendlyByteBuf(source.readRetainedSlice(p0)); }
    @Override public io.netty.buffer.ByteBuf readBytes(io.netty.buffer.ByteBuf p0) { source.readBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(io.netty.buffer.ByteBuf p0, int p1) { source.readBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(io.netty.buffer.ByteBuf p0, int p1, int p2) { source.readBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(byte[] p0) { source.readBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(byte[] p0, int p1, int p2) { source.readBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(java.nio.ByteBuffer p0) { source.readBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf readBytes(java.io.OutputStream p0, int p1) throws java.io.IOException { source.readBytes(p0, p1); return this; }
    @Override public int readBytes(java.nio.channels.GatheringByteChannel p0, int p1) throws java.io.IOException { return source.readBytes(p0, p1); }
    @Override public java.lang.CharSequence readCharSequence(int p0, java.nio.charset.Charset p1) { return source.readCharSequence(p0, p1); }
    @Override public int readBytes(java.nio.channels.FileChannel p0, long p1, int p2) throws java.io.IOException { return source.readBytes(p0, p1, p2); }
    @Override public io.netty.buffer.ByteBuf skipBytes(int p0) { source.skipBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeBoolean(boolean p0) { source.writeBoolean(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeByte(int p0) { source.writeByte(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeShort(int p0) { source.writeShort(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeShortLE(int p0) { source.writeShortLE(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeMedium(int p0) { source.writeMedium(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeMediumLE(int p0) { source.writeMediumLE(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeInt(int p0) { source.writeInt(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeIntLE(int p0) { source.writeIntLE(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeLong(long p0) { source.writeLong(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeLongLE(long p0) { source.writeLongLE(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeChar(int p0) { source.writeChar(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeFloat(float p0) { source.writeFloat(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeDouble(double p0) { source.writeDouble(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(io.netty.buffer.ByteBuf p0) { source.writeBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(io.netty.buffer.ByteBuf p0, int p1) { source.writeBytes(p0, p1); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(io.netty.buffer.ByteBuf p0, int p1, int p2) { source.writeBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(byte[] p0) { source.writeBytes(p0); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(byte[] p0, int p1, int p2) { source.writeBytes(p0, p1, p2); return this; }
    @Override public io.netty.buffer.ByteBuf writeBytes(java.nio.ByteBuffer p0) { source.writeBytes(p0); return this; }
    @Override public int writeBytes(java.io.InputStream p0, int p1) throws java.io.IOException { return source.writeBytes(p0, p1); }
    @Override public int writeBytes(java.nio.channels.ScatteringByteChannel p0, int p1) throws java.io.IOException { return source.writeBytes(p0, p1); }
    @Override public int writeBytes(java.nio.channels.FileChannel p0, long p1, int p2) throws java.io.IOException { return source.writeBytes(p0, p1, p2); }
    @Override public io.netty.buffer.ByteBuf writeZero(int p0) { source.writeZero(p0); return this; }
    @Override public int writeCharSequence(java.lang.CharSequence p0, java.nio.charset.Charset p1) { return source.writeCharSequence(p0, p1); }
    @Override public int indexOf(int p0, int p1, byte p2) { return source.indexOf(p0, p1, p2); }
    @Override public int bytesBefore(byte p0) { return source.bytesBefore(p0); }
    @Override public int bytesBefore(int p0, byte p1) { return source.bytesBefore(p0, p1); }
    @Override public int bytesBefore(int p0, int p1, byte p2) { return source.bytesBefore(p0, p1, p2); }
    @Override public int forEachByte(io.netty.util.ByteProcessor p0) { return source.forEachByte(p0); }
    @Override public int forEachByte(int p0, int p1, io.netty.util.ByteProcessor p2) { return source.forEachByte(p0, p1, p2); }
    @Override public int forEachByteDesc(io.netty.util.ByteProcessor p0) { return source.forEachByteDesc(p0); }
    @Override public int forEachByteDesc(int p0, int p1, io.netty.util.ByteProcessor p2) { return source.forEachByteDesc(p0, p1, p2); }
    @Override public io.netty.buffer.ByteBuf copy() { return new FriendlyByteBuf(source.copy()); }
    @Override public io.netty.buffer.ByteBuf copy(int p0, int p1) { return new FriendlyByteBuf(source.copy(p0, p1)); }
    @Override public io.netty.buffer.ByteBuf slice() { return new FriendlyByteBuf(source.slice()); }
    @Override public io.netty.buffer.ByteBuf retainedSlice() { return new FriendlyByteBuf(source.retainedSlice()); }
    @Override public io.netty.buffer.ByteBuf slice(int p0, int p1) { return new FriendlyByteBuf(source.slice(p0, p1)); }
    @Override public io.netty.buffer.ByteBuf retainedSlice(int p0, int p1) { return new FriendlyByteBuf(source.retainedSlice(p0, p1)); }
    @Override public io.netty.buffer.ByteBuf duplicate() { return new FriendlyByteBuf(source.duplicate()); }
    @Override public io.netty.buffer.ByteBuf retainedDuplicate() { return new FriendlyByteBuf(source.retainedDuplicate()); }
    @Override public int nioBufferCount() { return source.nioBufferCount(); }
    @Override public java.nio.ByteBuffer nioBuffer() { return source.nioBuffer(); }
    @Override public java.nio.ByteBuffer nioBuffer(int p0, int p1) { return source.nioBuffer(p0, p1); }
    @Override public java.nio.ByteBuffer internalNioBuffer(int p0, int p1) { return source.internalNioBuffer(p0, p1); }
    @Override public java.nio.ByteBuffer[] nioBuffers() { return source.nioBuffers(); }
    @Override public java.nio.ByteBuffer[] nioBuffers(int p0, int p1) { return source.nioBuffers(p0, p1); }
    @Override public boolean hasArray() { return source.hasArray(); }
    @Override public byte[] array() { return source.array(); }
    @Override public int arrayOffset() { return source.arrayOffset(); }
    @Override public boolean hasMemoryAddress() { return source.hasMemoryAddress(); }
    @Override public long memoryAddress() { return source.memoryAddress(); }
    @Override public java.lang.String toString(java.nio.charset.Charset p0) { return source.toString(p0); }
    @Override public java.lang.String toString(int p0, int p1, java.nio.charset.Charset p2) { return source.toString(p0, p1, p2); }
    @Override public int hashCode() { return source.hashCode(); }
    @Override public boolean equals(java.lang.Object p0) { return source.equals(p0); }
    @Override public int compareTo(io.netty.buffer.ByteBuf p0) { return source.compareTo(p0); }
    @Override public java.lang.String toString() { return source.toString(); }
    @Override public io.netty.buffer.ByteBuf retain(int p0) { source.retain(p0); return this; }
    @Override public io.netty.buffer.ByteBuf retain() { source.retain(); return this; }
    @Override public io.netty.buffer.ByteBuf touch() { source.touch(); return this; }
    @Override public io.netty.buffer.ByteBuf touch(java.lang.Object p0) { source.touch(p0); return this; }

}
