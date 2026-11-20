package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** T-228: 서명 관문 — 합성 서명표로 비교 규칙과 기준선 판정을 시험한다(바닐라 파일 없이 돈다). */
class SigGateTest {
    private static final int PUB = Opcodes.ACC_PUBLIC;

    /** 클래스 한 개. members 는 {"M|F", 접근, 이름, 서술자} 묶음. */
    private static byte[] cls(String name, String superName, int access, String[] itfs, Object[]... members) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, access, name, null, superName, itfs);
        for (Object[] m : members) {
            if (m[0].equals("F")) cw.visitField((Integer) m[1], (String) m[2], (String) m[3], null, null).visitEnd();
            else cw.visitMethod((Integer) m[1], (String) m[2], (String) m[3], null, null).visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Object[] m(int access, String name, String desc) {
        return new Object[] {"M", access, name, desc};
    }

    private static SigTable table(byte[]... classes) {
        SigTable t = SigTable.empty("t");
        for (byte[] c : classes) t.add(c);
        return t;
    }

    private static List<String> codes(List<SigGate.Diff> d) {
        return d.stream().map(x -> x.code() + ":" + x.owner() + ":" + x.member()).toList();
    }

    @Test
    void identicalDeclarationsHaveNoDiffs() {
        byte[] a = cls("a/A", "java/lang/Object", PUB, null, m(PUB, "f", "()I"));
        SigGate g = new SigGate(table(a), List.of(table(a)));
        assertTrue(g.compare().isEmpty());
        assertTrue(g.info().isEmpty());
    }

    @Test
    void inheritedMemberRedeclaredInSubclassIsNotAHole() {
        // 바닐라: Base 가 dimension() 선언. shim: Sub 가 다시 선언(상위 사슬로 풀린다) → 차이 아님
        SigTable ref = table(cls("a/Base", "java/lang/Object", PUB, null, m(PUB, "dim", "()I")), cls("a/Sub", "a/Base", PUB, null));
        SigTable shim = table(cls("a/Base", "java/lang/Object", PUB, null, m(PUB, "dim", "()I")), cls("a/Sub", "a/Base", PUB, null, m(PUB, "dim", "()I")));
        assertTrue(new SigGate(shim, List.of(ref)).compare().isEmpty());
    }

    @Test
    void superInterfaceAndKindDifferencesAreDiffs() {
        SigTable ref = table(cls("a/Itf", "java/lang/Object", PUB | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, null),
                cls("a/X", "a/P", PUB, new String[] {"a/Itf"}), cls("a/P", "java/lang/Object", PUB, null),
                cls("a/Y", "java/lang/Object", PUB, new String[] {"a/Itf"}));
        SigTable shim = table(cls("a/Itf", "java/lang/Object", PUB | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, null),
                cls("a/X", "java/lang/Object", PUB, null), cls("a/P", "java/lang/Object", PUB, null),
                cls("a/Y", "java/lang/Object", PUB, null));
        List<String> c = codes(new SigGate(shim, List.of(ref)).compare());
        assertTrue(c.contains("super:a/X:-"), c.toString());
        assertFalse(c.contains("type-missing:a/X:-"), "상위가 다르면 super 한 건이 말한다");
        assertTrue(c.contains("type-missing:a/Y:-"), c.toString());
    }

    @Test
    void jdkTypesAreNotCountedAsMissing() {
        SigTable ref = table(cls("a/R", "java/lang/Object", PUB, new String[] {"java/lang/Runnable"}));
        SigTable shim = table(cls("a/R", "java/lang/Object", PUB, null));
        assertTrue(new SigGate(shim, List.of(ref)).compare().isEmpty());
    }

    @Test
    void descriptorStaticAndAccessDifferencesFailButExtrasAreInfoOnly() {
        SigTable ref = table(cls("a/A", "java/lang/Object", PUB, null,
                m(PUB, "ret", "()I"), m(PUB, "inst", "()V"), m(PUB | Opcodes.ACC_STATIC, "stat", "()V"), m(PUB, "ov", "(I)V"), m(PUB, "ov", "(J)V")));
        SigTable shim = table(cls("a/A", "java/lang/Object", PUB, null,
                m(PUB, "ret", "()J"),                         // 반환형 다름 → descriptor
                m(PUB | Opcodes.ACC_STATIC, "inst", "()V"),   // static 다름
                m(PUB, "stat", "()V"),                        // static 다름(반대)
                m(PUB, "ov", "(I)V"), m(PUB, "ov", "(J)V"), m(PUB, "ov", "(D)V"),   // 오버로드 더함 → 참고
                m(PUB, "helper", "()V")),                     // Furnace 내부 도우미 → 참고
                cls("a/Extra", "java/lang/Object", PUB, null));
        SigGate g = new SigGate(shim, List.of(ref));
        List<String> c = codes(g.compare());
        assertTrue(c.contains("descriptor:a/A:ret()J"), c.toString());
        assertTrue(c.contains("static:a/A:inst()V"), c.toString());
        assertTrue(c.contains("static:a/A:stat()V"), c.toString());
        assertEquals(3, c.size(), c.toString());
        List<String> i = codes(g.info());
        assertTrue(i.contains("extra-overload:a/A:ov(D)V"), i.toString());
        assertTrue(i.contains("extra-member:a/A:helper()V"), i.toString());
        assertTrue(i.contains("extra-class:a/Extra:-"), i.toString());
    }

    @Test
    void privateShimMembersAreNotCompared() {
        SigTable ref = table(cls("a/A", "java/lang/Object", PUB, null));
        SigTable shim = table(cls("a/A", "java/lang/Object", PUB, null, m(Opcodes.ACC_PRIVATE, "secret", "()V")));
        SigGate g = new SigGate(shim, List.of(ref));
        assertTrue(g.compare().isEmpty());
        assertTrue(g.info().isEmpty());
    }

    @Test
    void baselineJudgeFlagsFreshStaleAndMissingReason() {
        SigGate.Diff known = new SigGate.Diff("super", "a/A", "-", "shim=x ref=y");
        SigGate.Diff fresh = new SigGate.Diff("descriptor", "a/B", "m()V", "기준=[]");
        Map<String, String> base = new java.util.LinkedHashMap<>();
        base.put(known.key(), "의도: 단일 서버 스레드 가정을 주지 않기 위해");
        base.put("super\ta/Gone\t-\tshim=x ref=y", "이제 같아졌다");
        SigGateCommand.Result r = SigGateCommand.judge(List.of(known, fresh), base);
        assertEquals(List.of(fresh.key()), r.fresh());
        assertEquals(List.of("super\ta/Gone\t-\tshim=x ref=y"), r.stale());
        assertTrue(r.noReason().isEmpty());
        assertFalse(r.ok());
        base.put(known.key(), " ");
        assertEquals(List.of(known.key()), SigGateCommand.judge(List.of(known), Map.of(known.key(), " ")).noReason());
        assertTrue(SigGateCommand.judge(List.of(known), Map.of(known.key(), "이유")).ok());
    }

    @Test
    void baselineRoundTripKeepsReasonsAndFillsDefaultsForNew(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("b.txt");
        SigGate.Diff a = new SigGate.Diff("super", "a/A", "-", "d1");
        SigGate.Diff b = new SigGate.Diff("static", "a/B", "m()V", "d2");
        SigGateCommand.writeBaseline(f, List.of(a), Map.of());
        String first = Files.readString(f);
        assertTrue(first.contains("super\ta/A\t-\td1\t미분류 — 상위 클래스 사슬"), first);
        Files.writeString(f, first.replace("미분류 — 상위 클래스 사슬이 바닐라와 다르다. 손 shim 의 알려진 차이, 생성기(T-229~)가 바로잡으면 이 줄은 지운다", "의도한 차이"));
        SigGateCommand.writeBaseline(f, List.of(a, b), SigGateCommand.readBaseline(f));
        Map<String, String> back = SigGateCommand.readBaseline(f);
        assertEquals("의도한 차이", back.get(a.key()));
        assertTrue(back.get(b.key()).startsWith("미분류 — static"), back.toString());
    }
}
