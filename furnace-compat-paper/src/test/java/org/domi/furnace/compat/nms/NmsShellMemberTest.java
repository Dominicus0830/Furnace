package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.furnacetest.ShellFieldHolder;
import org.domi.furnace.api.plugin.CompatGap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-260: 전체 껍데기의 동작 없는 멤버(@NmsShellMember)는 NmsSymbols 가 "없음"으로 본다(플러그인 호출 자리에 덫) — 있는 것은 그대로.
 * 리플렉션으로 그런 필드를 읽고 쓰면 값을 지어내지 않고 던지며, 던지는 몸통은 compat gaps 표에 센다.
 */
class NmsShellMemberTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { NmsGaps.reset(); }

    private static final String MARK = "Lorg/domi/furnace/compat/nms/NmsShellMember;";

    @Test
    void markedMembersCountAsMissing() throws Exception {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/u/Shell", null, "java/lang/Object", null);
        FieldVisitor f = w.visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null);
        f.visitAnnotation(MARK, false).visitEnd();
        f.visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "LIMIT", "I", null, 7).visitEnd();
        FieldVisitor pf = w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "cache", "Ljava/lang/Object;", null, null);
        pf.visitAnnotation(MARK, false).visitEnd();
        pf.visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "size", "()I", null, null);
        m.visitAnnotation(MARK, false).visitEnd();
        m.visitEnd();
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitAnnotation(MARK, false).visitEnd();
        c.visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "draw", "()V", null, null).visitEnd();
        w.visitEnd();
        Path p = tmp.resolve("net/minecraft/u/Shell.class");
        Files.createDirectories(p.getParent());
        Files.write(p, w.toByteArray());
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.toUri().toURL()}, null)) {
            NmsSymbols s = new NmsSymbols(cl);
            assertTrue(s.hasClass("net/minecraft/u/Shell"), "클래스는 있다(instanceof·상속 모양)");
            assertFalse(s.hasField("net/minecraft/u/Shell", "count", "I"));
            assertTrue(s.hasField("net/minecraft/u/Shell", "LIMIT", "I"), "상수는 값 그대로 있다");
            assertFalse(s.hasMethod("net/minecraft/u/Shell", "size", "()I"));
            assertFalse(s.hasMethod("net/minecraft/u/Shell", "<init>", "()V"));
            assertTrue(s.hasMethod("net/minecraft/u/Shell", "draw", "()V"), "추상 선언은 해석된다");
            assertTrue(s.isShellField("net/minecraft/u/Shell", "count", "I"));
            assertFalse(s.isShellField("net/minecraft/u/Shell", "LIMIT", "I"));
            assertFalse(s.hasField("net/minecraft/u/Shell", "cache", "Ljava/lang/Object;"), "private 은 링크할 수 없다");
            assertTrue(s.isShellField("net/minecraft/u/Shell", "cache", "Ljava/lang/Object;"), "private 껍데기 필드도 표지는 모은다(리플렉션 덫)");
        }
    }

    @Test
    void reflectiveAccessToShellFieldsThrows() throws Exception {
        ShellFieldHolder h = new ShellFieldHolder();
        Field shell = ShellFieldHolder.class.getField("shellValue");
        Field live = ShellFieldHolder.class.getField("liveValue");
        UnsupportedNmsCall e = assertThrows(UnsupportedNmsCall.class, () -> NmsReflect.beforeFieldGet(shell, h));
        assertTrue(e.getMessage().contains("net.minecraft.furnacetest.ShellFieldHolder#shellValue:I"), e.getMessage());
        assertThrows(UnsupportedNmsCall.class, () -> NmsReflect.afterFieldSet(shell, h));
        NmsReflect.beforeFieldGet(live, h);
        NmsReflect.afterFieldSet(live, h);
        assertEquals(5, live.getInt(h));
        List<CompatGap> g = NmsGaps.snapshot();
        assertEquals(1, g.size(), g.toString());
        assertEquals("field", g.get(0).kind());
        assertEquals(2, g.get(0).calls());
    }

    @Test
    void privateShellFieldsAreTrappedThroughReflection() throws Exception {
        Field hidden = ShellFieldHolder.class.getDeclaredField("hidden");
        hidden.setAccessible(true);
        UnsupportedNmsCall e = assertThrows(UnsupportedNmsCall.class, () -> NmsReflect.beforeFieldGet(hidden, null));
        assertTrue(e.getMessage().contains("ShellFieldHolder#hidden"), e.getMessage());
        assertThrows(UnsupportedNmsCall.class, () -> NmsReflect.afterFieldSet(hidden, null));
    }

    @Test
    void classInitTrapCountsAsClass() {
        Error e = NmsShell.unsupportedClass("io.papermc.u.Holder");
        assertTrue(e instanceof NoClassDefFoundError && e instanceof UnsupportedNmsLink, e.toString());
        List<CompatGap> g = NmsGaps.snapshot();
        assertEquals(1, g.size());
        assertEquals("class", g.get(0).kind());
        assertEquals("io.papermc.u.Holder", g.get(0).target());
    }

    @Test
    void shellBodiesCountAsMethodCalls() {
        RuntimeException e = NmsShell.unsupported("net.minecraft.u.Shell#size()I");
        assertTrue(e instanceof UnsupportedNmsCall);
        List<CompatGap> g = NmsGaps.snapshot();
        assertEquals(1, g.size());
        assertEquals("method", g.get(0).kind());
        assertEquals("net.minecraft.u.Shell#size()I", g.get(0).target());
    }
}
