// C1-02: 디스크·스트림 NBT 읽기의 깊이 한도. 바닐라 NbtAccounter(maxDepth 512)는 compound·list 가 513겹이면 "Tried to read NBT tag with too high
// complexity, depth > 512" 로 거부한다. 한도가 없으면 깊이 수만 겹 파일이 StackOverflowError 로 읽는 스레드(IO·접속)를 죽인다.
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class NbtIoDepthLimitTest {

    /** 루트 compound 포함 levels 겹의 compound 중첩 [10][""] {a: {a: ...{}}} 바이트. */
    private static byte[] nestedCompounds(int levels) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeByte(NbtIo.TAG_COMPOUND);
        o.writeUTF("");
        for (int i = 1; i < levels; i++) {
            o.writeByte(NbtIo.TAG_COMPOUND);
            o.writeUTF("a");
        }
        for (int i = 0; i < levels; i++) o.writeByte(NbtIo.TAG_END);
        return b.toByteArray();
    }

    /** 루트 compound → list 중첩 levels-1 겹(마지막 list 는 빈 compound 목록이 아니라 빈 list). */
    private static byte[] nestedLists(int levels) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeByte(NbtIo.TAG_COMPOUND);
        o.writeUTF("");
        o.writeByte(NbtIo.TAG_LIST);
        o.writeUTF("l");
        for (int i = 2; i < levels; i++) {   // 루트 1겹 + list levels-1겹(첫 list 는 위에서 이미)
            o.writeByte(NbtIo.TAG_LIST);   // 원소 종류 = list
            o.writeInt(1);
        }
        o.writeByte(NbtIo.TAG_END);   // 가장 안쪽 list: 종류 END, 길이 0
        o.writeInt(0);
        o.writeByte(NbtIo.TAG_END);   // 루트 끝
        return b.toByteArray();
    }

    private static void readBoth(byte[] bytes, boolean expectOk) throws IOException {
        if (expectOk) {
            NbtIo.read(bytes);
            NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } else {
            var e1 = assertThrows(IOException.class, () -> NbtIo.read(bytes));
            assertTrue(e1.getMessage().contains("depth > 512"), e1.getMessage());
            var e2 = assertThrows(IOException.class, () -> NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes))));
            assertTrue(e2.getMessage().contains("depth > 512"), e2.getMessage());
        }
    }

    @Test
    void compounds_512Levels_ok_513_rejected() throws Exception {
        readBoth(nestedCompounds(512), true);
        readBoth(nestedCompounds(513), false);
    }

    @Test
    void lists_512Levels_ok_513_rejected() throws Exception {
        readBoth(nestedLists(512), true);
        readBoth(nestedLists(513), false);
    }

    @Test
    void veryDeepFile_isRejectedNotStackOverflow() throws Exception {
        readBoth(nestedCompounds(200_000), false);
    }

    @Test
    void gzipReader_alsoLimited() throws Exception {
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (var z = new java.util.zip.GZIPOutputStream(gz)) { z.write(nestedCompounds(600)); }
        assertThrows(IOException.class, () -> NbtIo.readGzip(new ByteArrayInputStream(gz.toByteArray())));
    }
}
