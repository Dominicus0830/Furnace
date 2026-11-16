package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class MojangMappingsTest {
    static final String SAMPLE = """
            # comment
            net.minecraft.core.BlockPos -> jd:
                int x -> a
                long packed -> a
                16:16:net.minecraft.core.BlockPos offset(int,int,int) -> a
                17:18:net.minecraft.core.BlockPos offset(net.minecraft.core.Direction) -> a
                1:1:void <init>(int,int,int) -> <init>
            net.minecraft.core.Direction -> ji:
                net.minecraft.core.Direction[] VALUES -> b
                net.minecraft.core.Direction[] values() -> a
            net.minecraft.world.level.block.state.BlockBehaviour$Properties -> dtb$d:
                boolean hasCollision -> c
            """;

    @Test
    void parsesClassesAndOverloadedMembers() throws IOException {
        MojangMappings m = MojangMappings.parse(new StringReader(SAMPLE));
        assertEquals(3, m.classCount());
        assertEquals("net/minecraft/core/BlockPos", m.mapClass("jd"));
        assertEquals("net/minecraft/world/level/block/state/BlockBehaviour$Properties", m.mapClass("dtb$d"));
        assertEquals("unknown/Lib", m.mapClass("unknown/Lib"));

        MojangMappings.ClassMap pos = m.byObf("jd");
        // 같은 난독화 이름 a 가 서술자로 구분된다: 필드 int a / long a, 메서드 두 개
        assertEquals("x", pos.field("a", "I"));
        assertEquals("packed", pos.field("a", "J"));
        assertNull(pos.field("a", "Z"));
        assertEquals("offset", pos.method("a", "(III)Ljd;"));
        assertEquals("offset", pos.method("a", "(Lji;)Ljd;"));
        assertNull(pos.method("a", "()V"));
        assertEquals("<init>", pos.method("<init>", "(III)V"));
    }

    @Test
    void descriptorsUseObfuscatedClassNamesAndLeaveLibrariesAlone() throws IOException {
        MojangMappings m = MojangMappings.parse(new StringReader(SAMPLE));
        assertEquals("[Lji;", m.byObf("ji").fields.keySet().stream().filter(k -> k.startsWith("b:")).findFirst().orElseThrow().substring(2));
        assertEquals("Ljava/lang/String;", m.descriptorOf("java.lang.String"));
        assertEquals("[[I", m.descriptorOf("int[][]"));
        assertEquals("V", m.descriptorOf("void"));
        assertEquals("Ljd;", m.descriptorOf("net.minecraft.core.BlockPos"));
    }

    @Test
    void rejectsMalformedLines() {
        assertThrows(IOException.class, () -> MojangMappings.parse(new StringReader("    int x -> a\n")));
        assertThrows(IOException.class, () -> MojangMappings.parse(new StringReader("a.B -> c:\n    ???\n")));
    }
}
