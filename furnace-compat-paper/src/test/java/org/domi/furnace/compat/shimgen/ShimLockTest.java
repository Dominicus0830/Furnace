package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.domi.furnace.tools.extract.SigTable;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** 잠금 파일 풀기(T-229): 명세 + 서명표 → 상위 사슬·멤버 서술자·제네릭 서명, 그리고 실패 보고. 서명표는 ASM 으로 지은 가짜 클래스로 만든다. */
class ShimLockTest {

    static byte[] cls(int access, String name, String sig, String sup, String[] itfs, String[]... members) {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V21, access, name, sig, sup, itfs);
        for (String[] m : members) {
            int acc = Integer.parseInt(m[1]);
            if (m[0].equals("F")) w.visitField(acc, m[2], m[3], m.length > 4 ? m[4] : null, null).visitEnd();
            else w.visitMethod(acc, m[2], m[3], m.length > 4 ? m[4] : null, null).visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    static SigTable table() {
        SigTable t = SigTable.empty("vanilla-test");
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Thing", "Lnet/minecraft/a/Base<Lnet/minecraft/a/Gone;>;", "net/minecraft/a/Base",
                new String[] {"net/minecraft/a/Named", "java/lang/Comparable"},
                new String[] {"M", "1", "getX", "()D"},
                new String[] {"M", "1", "move", "(D)V"},
                new String[] {"M", "1", "move", "(DDD)V"},
                new String[] {"M", "1", "level", "()Lnet/minecraft/a/Level;"},
                new String[] {"M", "1", "list", "()Ljava/util/List;", "()Ljava/util/List<Lnet/minecraft/a/Gone;>;"},
                new String[] {"F", "25", "MAX", "I"}));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, "net/minecraft/a/Base", "<T:Ljava/lang/Object;>Ljava/lang/Object;", "java/lang/Object", null,
                new String[] {"M", "1", "getX", "()D"}));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "net/minecraft/a/Named", null, "java/lang/Object", new String[] {"net/minecraft/a/Root"}));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "net/minecraft/a/Root", null, "java/lang/Object", null));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Level", null, "java/lang/Object", null));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Gone", null, "java/lang/Object", null));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, "net/minecraft/a/Mode", "Ljava/lang/Enum<Lnet/minecraft/a/Mode;>;", "java/lang/Enum", null,
                new String[] {"F", "16409", "B", "Lnet/minecraft/a/Mode;"},
                new String[] {"F", "16409", "A", "Lnet/minecraft/a/Mode;"},
                new String[] {"F", "16409", "C", "Lnet/minecraft/a/Mode;"},
                new String[] {"F", "4122", "$VALUES", "[Lnet/minecraft/a/Mode;"}));
        return t;
    }

    static ShimSpec spec(String text) {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", text);
        return s;
    }

    @Test
    void resolvesTheSupertypeChainAndOnlySpecMembers() {
        ShimLock lock = ShimLock.resolve("t", spec("class net.minecraft.a.Thing\n  getX -> org.example.X.y pure\n  move(DDD) -> org.example.X.m pure\n  MAX = 3\n"),
                List.of(table()), n -> false);
        assertEquals(Set.of("net/minecraft/a/Thing", "net/minecraft/a/Base", "net/minecraft/a/Named", "net/minecraft/a/Root"), lock.classes.keySet(),
                "명세 클래스 + 상위·인터페이스 사슬(net.minecraft 만, java.lang.Comparable 은 아님)");
        assertEquals(List.of("MAX I", "getX ()D", "move (DDD)V"), lock.members("net/minecraft/a/Thing").stream().map(ShimLock.Member::key).toList());
        assertEquals(List.of(), lock.members("net/minecraft/a/Base"), "사슬 클래스는 멤버 없이 선언만");
        assertNull(lock.classes.get("net/minecraft/a/Thing").signature(), "shim 에 없는 형식(Gone)을 가리키는 제네릭 서명은 지운다(Mojang 이름을 잠금에 남기지 않는다)");
        assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Object;", lock.classes.get("net/minecraft/a/Base").signature());
        assertEquals("vanilla-test", lock.classes.get("net/minecraft/a/Thing").source());
        // 쓰고 읽으면 같다
        assertEquals(lock.text(), ShimLock.parse(lock.text()).text());
    }

    @Test
    void keepsAGenericSignatureWhenEveryTypeIsInTheShim() {
        ShimLock lock = ShimLock.resolve("t", spec("class net.minecraft.a.Thing\n  list -> org.example.X.l pure\n"), List.of(table()), n -> n.equals("net/minecraft/a/Gone"));
        assertEquals("()Ljava/util/List<Lnet/minecraft/a/Gone;>;", lock.members("net/minecraft/a/Thing").get(0).signature());
        assertEquals("Lnet/minecraft/a/Base<Lnet/minecraft/a/Gone;>;", lock.classes.get("net/minecraft/a/Thing").signature());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        ShimLock.ResolveException e = assertThrows(ShimLock.ResolveException.class, () -> ShimLock.resolve("t", spec("""
                class net.minecraft.a.Thing
                  move -> org.example.X.m pure
                  level -> org.example.X.l read=any
                  nope -> org.example.X.n pure
                class net.minecraft.a.Missing
                """), List.of(table()), n -> false));
        List<String> p = e.problems;
        assertEquals(4, p.size(), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("class not in the signature tables: net/minecraft/a/Missing")), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("overloaded name - write the descriptor: move")), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("uses a type that is not in the shim: net/minecraft/a/Level")), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("does not declare member nope")), String.join("\n", p));
    }

    @Test
    void recordsTheEnumConstantCountNotTheNames() {
        ShimLock lock = ShimLock.resolve("t", spec("class net.minecraft.a.Mode\n  A\n"), List.of(table()), n -> false);
        assertEquals(3, lock.enumConstants.get("net/minecraft/a/Mode"), "바닐라 상수 개수($VALUES 같은 합성 필드는 빼고)");
        assertTrue(lock.text().contains("E\tnet/minecraft/a/Mode\t3\n"), lock.text());
        assertEquals(lock.text(), ShimLock.parse(lock.text()).text());
    }

    @Test
    void windowsLineEndingsAreTheSameLock() {
        ShimLock lock = ShimLock.resolve("t", spec("class net.minecraft.a.Thing\n  getX -> org.example.X.y pure\n"), List.of(table()), n -> false);
        String crlf = lock.text().replace("\n", "\r\n");
        assertTrue(ShimLock.same(lock.text(), crlf), "core.autocrlf 체크아웃(CRLF)도 같은 잠금");
        assertEquals(List.of(), ShimLock.diff(lock.text(), crlf));
        assertEquals(lock.text(), ShimLock.parse(crlf).text());
        assertFalse(ShimLock.same(lock.text(), crlf.replace("getX", "getY")));
    }

    @Test
    void earlierTablesWin() {
        SigTable paper = SigTable.empty("paper-test");
        paper.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Level", null, "java/lang/Object", new String[] {"org/bukkit/World"},
                new String[] {"M", "1", "paperOnly", "()V"}));
        ShimLock lock = ShimLock.resolve("t", spec("class net.minecraft.a.Level\n  paperOnly -> @hand pure\n"), List.of(paper, table()), n -> false);
        assertEquals("paper-test", lock.classes.get("net/minecraft/a/Level").source());
        assertEquals(List.of("org/bukkit/World"), lock.classes.get("net/minecraft/a/Level").interfaces());
    }

    /** Paper 내부(ca/spottedleaf) 인터페이스를 구현하는 제네릭 클래스: 인터페이스만 글자 그대로 걸러 내고 형식 매개변수·상위·남는 인터페이스는 그대로 둔다. */
    @Test
    void genericClassKeepsItsSignatureWhenPaperInternalInterfacesAreDropped() {
        SigTable t = SigTable.empty("paper-test");
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Holder",
                "<O:Ljava/lang/Object;S:Lnet/minecraft/a/Holder<TO;TS;>;>Ljava/lang/Object;Lca/spottedleaf/a/Patched<TO;>;Lnet/minecraft/a/Named;Lca/spottedleaf/a/Other;",
                "java/lang/Object", new String[] {"ca/spottedleaf/a/Patched", "net/minecraft/a/Named", "ca/spottedleaf/a/Other"}));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "net/minecraft/a/Named", null, "java/lang/Object", null));
        ShimLock lock = ShimLock.resolve("test", spec("class net.minecraft.a.Holder\n"), List.of(t), n -> false);
        ShimLock.Cls c = lock.classes.get("net/minecraft/a/Holder");
        assertEquals(List.of("net/minecraft/a/Named"), c.interfaces());
        assertEquals("<O:Ljava/lang/Object;S:Lnet/minecraft/a/Holder<TO;TS;>;>Ljava/lang/Object;Lnet/minecraft/a/Named;", c.signature());
    }

    /** 걸러 낸 뒤에도 형식 경계에 Paper 내부 형식이 남으면 클래스 이름과 함께 분명한 오류를 낸다. */
    @Test
    void paperInternalTypeLeftInATypeBoundIsReported() {
        SigTable t = SigTable.empty("paper-test");
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "net/minecraft/a/Holder",
                "<O:Lca/spottedleaf/a/Patched;>Ljava/lang/Object;Lnet/minecraft/a/Named;",
                "java/lang/Object", new String[] {"net/minecraft/a/Named"}));
        t.add(cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "net/minecraft/a/Named", null, "java/lang/Object", null));
        ShimLock.ResolveException e = assertThrows(ShimLock.ResolveException.class,
                () -> ShimLock.resolve("test", spec("class net.minecraft.a.Holder\n"), List.of(t), n -> false));
        assertTrue(e.getMessage().contains("net/minecraft/a/Holder") && e.getMessage().contains("ca/spottedleaf/a/Patched"), e.getMessage());
    }
}
