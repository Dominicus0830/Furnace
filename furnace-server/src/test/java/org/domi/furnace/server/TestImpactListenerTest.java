// TestImpactListener 의 JaCoCo 실행 데이터(.exec) 읽기 시험. 에이전트 없이 손으로 만든 바이트로 형식을 확인한다.
package org.domi.furnace.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestImpactListenerTest {

    private static void header(DataOutputStream out) throws IOException {
        out.writeByte(0x01);
        out.writeChar(0xC0C0);
        out.writeChar(0x1007);
    }

    private static void exec(DataOutputStream out, long id, String name, boolean... probes) throws IOException {
        out.writeByte(0x11);
        out.writeLong(id);
        out.writeUTF(name);
        // 가변 정수 길이 + 8칸씩 묶은 바이트(JaCoCo CompactDataOutput)
        int len = probes.length;
        while ((len & 0xFFFFFF80) != 0) {
            out.writeByte(0x80 | (len & 0x7F));
            len >>>= 7;
        }
        out.writeByte(len);
        int buffer = 0, bit = 0;
        for (boolean p : probes) {
            if (p) buffer |= 1 << bit;
            if (++bit == 8) { out.writeByte(buffer); buffer = 0; bit = 0; }
        }
        if (bit > 0) out.writeByte(buffer);
    }

    @Test
    void onlyClassesWithAHitProbeAreReported() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        header(out);
        out.writeByte(0x10); out.writeUTF("session"); out.writeLong(1); out.writeLong(2);
        exec(out, 1, "a/b/Hit", false, true, false);
        exec(out, 2, "a/b/Loaded$NotRun", false, false, false, false, false, false, false, false, false);   // 9칸 = 바이트 둘
        exec(out, 3, "a/b/Late", false, false, false, false, false, false, false, false, false, false, true);  // 둘째 바이트의 칸
        exec(out, 4, "a/b/Hit$1", true);
        assertEquals(List.of("a/b/Hit", "a/b/Hit$1", "a/b/Late"), TestImpactListener.executedClasses(bytes.toByteArray()));
    }

    @Test
    void longProbeArraysUseAMultiByteLength() throws IOException {
        boolean[] many = new boolean[300];
        many[299] = true;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        header(out);
        exec(out, 1, "a/Big", many);
        exec(out, 2, "a/Next", true);
        assertEquals(List.of("a/Big", "a/Next"), TestImpactListener.executedClasses(bytes.toByteArray()));
    }

    @Test
    void unknownFormatIsRejectedInsteadOfReadAsEmpty() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0x01); out.writeChar(0xC0C0); out.writeChar(0x1008);
        assertThrows(IOException.class, () -> TestImpactListener.executedClasses(bytes.toByteArray()));
        assertThrows(IOException.class, () -> TestImpactListener.executedClasses(new byte[0]));
        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        DataOutputStream out2 = new DataOutputStream(bad);
        header(out2);
        out2.writeByte(0x7f);
        assertThrows(IOException.class, () -> TestImpactListener.executedClasses(bad.toByteArray()));
    }
}
