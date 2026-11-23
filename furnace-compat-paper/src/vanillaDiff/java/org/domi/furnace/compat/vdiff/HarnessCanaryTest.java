package org.domi.furnace.compat.vdiff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.domi.furnace.compat.vdiff.DiffRunner.MemberResult;
import org.domi.furnace.compat.vdiff.DiffRunner.Status;
import org.junit.jupiter.api.Test;

/** 틀 자체 점검: 일부러 틀린 구현은 "다름", 같은 구현은 "같음"이어야 한다(틀이 언제나 같음을 내는 고장을 막는다). 반환값·예외 종류·인자 상태(바이트)·NBT 내리기·paper 허용 범위 경로를 하나씩. */
class HarnessCanaryTest {

    /** 바닐라 Mth.floor 와 다르다 — 음수에서 0 쪽으로 자른다. */
    public static final class Wrong {
        public static int floor(double v) {
            return (int) v;
        }
    }

    /** 바닐라 Mth.floor 와 같은 규칙(자른 뒤 원래 값보다 크면 하나 뺀다 — 아주 큰 음수에서 넘침까지 같다). */
    public static final class Right {
        public static int floor(double v) {
            int t = (int) v;
            return t > v ? t - 1 : t;
        }
    }

    /** 예외 종류만 다르다(바닐라·shim 은 ResourceLocationException). */
    public static final class WrongException {
        public static net.minecraft.resources.ResourceLocation parse(String s) {
            try {
                return net.minecraft.resources.ResourceLocation.parse(s);
            } catch (net.minecraft.ResourceLocationException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /** 쓰기 인자(DataOutput)에 바이트 하나를 더 쓴다 — 인자 상태 비교 경로. */
    public static final class WrongBytes {
        public static void write(net.minecraft.nbt.CompoundTag t, java.io.DataOutput out) throws java.io.IOException {
            net.minecraft.nbt.NbtIo.write(t, out);
            out.writeByte(0);
        }
    }

    /** 같은 쓰기(대조군). */
    public static final class RightBytes {
        public static void write(net.minecraft.nbt.CompoundTag t, java.io.DataOutput out) throws java.io.IOException {
            net.minecraft.nbt.NbtIo.write(t, out);
        }
    }

    /** 읽은 태그가 비어 있지 않으면 빈 이름표를 준다 — NBT 내리기 경로. */
    public static final class WrongTag {
        public static net.minecraft.nbt.CompoundTag read(java.io.DataInput in) throws java.io.IOException {
            net.minecraft.nbt.CompoundTag t = net.minecraft.nbt.NbtIo.read(in);
            return t.isEmpty() ? t : new net.minecraft.nbt.CompoundTag();
        }
    }

    private static MemberResult run(Class<?> shim) throws Exception {
        return run(shim, "net.minecraft.util.Mth", "floor", double.class);
    }

    private static MemberResult run(Class<?> shim, String vanillaClass, String method, Class<?>... params) throws Exception {
        VanillaRuntime rt = VanillaRuntime.get();
        Class<?> vc = Class.forName(vanillaClass, true, rt.loader());
        Class<?>[] vps = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) vps[i] = TypeKit.resolve(params[i].getName(), rt.loader());
        Method m = vc.getMethod(method, vps);
        DiffRunner r = new DiffRunner(rt.loader(), HarnessCanaryTest.class.getClassLoader(), 1L);
        try {
            return r.run(vc, shim, m, 500);
        } finally {
            r.close();
        }
    }

    @Test
    void wrongExceptionKindIsCaught() throws Exception {
        MemberResult m = run(WrongException.class, "net.minecraft.resources.ResourceLocation", "parse", String.class);
        assertEquals(Status.DIFF, m.status(), String.valueOf(m));
    }

    @Test
    void extraOutputByteIsCaught() throws Exception {
        MemberResult m = run(WrongBytes.class, "net.minecraft.nbt.NbtIo", "write", net.minecraft.nbt.CompoundTag.class, java.io.DataOutput.class);
        assertEquals(Status.DIFF, m.status(), String.valueOf(m));
    }

    @Test
    void sameOutputBytesPass() throws Exception {
        MemberResult m = run(RightBytes.class, "net.minecraft.nbt.NbtIo", "write", net.minecraft.nbt.CompoundTag.class, java.io.DataOutput.class);
        assertEquals(Status.SAME, m.status(), String.valueOf(m));
    }

    @Test
    void wrongNbtResultIsCaught() throws Exception {
        MemberResult m = run(WrongTag.class, "net.minecraft.nbt.NbtIo", "read", java.io.DataInput.class);
        assertEquals(Status.DIFF, m.status(), String.valueOf(m));
    }

    /** paper 허용은 정한 예외 쌍에만 — 다른 차이는 같은 멤버여도 다름이다. */
    @Test
    void paperAllowanceDoesNotHideOtherDifferences() throws Exception {
        VanillaRuntime rt = VanillaRuntime.get();
        Class<?> vc = Class.forName("net.minecraft.nbt.NbtIo", true, rt.loader());
        Method m = vc.getMethod("read", java.io.DataInput.class);
        DiffRunner r = new DiffRunner(rt.loader(), HarnessCanaryTest.class.getClassLoader(), 1L);
        try {
            MemberResult res = r.run(vc, WrongTag.class, m, 500, java.util.List.of(
                    new DiffRunner.Allowance("net.minecraft.nbt.ReportedNbtException", "java.lang.IllegalArgumentException")));
            assertEquals(Status.DIFF, res.status(), String.valueOf(res));
        } finally {
            r.close();
        }
    }

    @Test
    void wrongImplementationIsCaught() throws Exception {
        assertEquals(Status.DIFF, run(Wrong.class).status());
    }

    @Test
    void equivalentImplementationPasses() throws Exception {
        MemberResult m = run(Right.class);
        assertEquals(Status.SAME, m.status(), String.valueOf(m));
    }
}
