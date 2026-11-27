package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** T-263 S5: 모양값(채움 표 X 줄)을 읽고 낸 클래스 초기화 코드가 값·객체 별칭·지도 종류를 그대로 만드는가. */
class ShapeValueTest {
    static final String VS = "Lnet/minecraft/world/phys/shapes/VoxelShape;";

    /** 정적 필드마다 모양값을 넣는 클래스를 만들어 올린다(검증기를 거친다). */
    static Class<?> build(String name, String[][] fields) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        for (String[] f : fields) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, f[0], f[1], null, null).visitEnd();
        MethodVisitor ci = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        ci.visitCode();
        for (String[] f : fields) ShapeValue.parse(f[2]).emitPut(ci, name, f[0], f[1]);
        ci.visitInsn(Opcodes.RETURN);
        ci.visitMaxs(ShapeValue.MAX_STACK, 0);   // 손 shim 에 끼울 때처럼 직접 적은 스택 크기로도 검증을 통과해야 한다
        ci.visitEnd();
        cw.visitEnd();
        byte[] b = cw.toByteArray();
        ClassLoader l = new ClassLoader(ShapeValueTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String n) throws ClassNotFoundException {
                if (!n.equals(name)) throw new ClassNotFoundException(n);
                return defineClass(n, b, 0, b.length);
            }
        };
        return Class.forName(name, true, l);
    }

    static Object get(Class<?> c, String f) throws Exception { return c.getField(f).get(null); }

    @Test
    void valuesAliasesAndMapKindsAreRebuilt() throws Exception {
        Class<?> c = build("S5Shapes", new String[][] {
            {"A", VS, "v 900001:0,0,0,1,0.5,1"},
            {"B", VS, "v 900001:0,0,0,1,0.5,1"},
            {"C", "[" + VS, "a 900002 3 900001:0,0,0,1,0.5,1 E 900003:0.25,0,0.25,0.75,1,0.75,0,0,0,1,0.125,1"},
            {"D", "Ljava/util/Map;", "m 900004 enum e:net/minecraft/core/Direction 2 SOUTH=B NORTH=900005:0,0,0,1,1,0.5"},
            {"E", "Ljava/util/Map;", "m 900006 hash i 2 1=E 2=900001:0,0,0,1,0.5,1"},
            {"F", "Ljava/util/Map;", "m 900007 imm e:net/minecraft/core/Direction 2 WEST=E EAST=B"},
            {"G", "Ljava/util/Map;", "m 900008 ienum e:net/minecraft/core/Direction 2 UP=E DOWN=B"},
            {"H", VS, "v 900009:"},
        });
        VoxelShape a = (VoxelShape) get(c, "A");
        assertArrayEquals(new double[] {0, 0, 0, 1, 0.5, 1}, a.furnaceBoxes());
        assertSame(a, get(c, "B"));   // 같은 번호 = 같은 객체
        VoxelShape[] arr = (VoxelShape[]) get(c, "C");
        assertSame(a, arr[0]);
        assertSame(Shapes.empty(), arr[1]);
        assertEquals(12, arr[2].furnaceBoxes().length);
        Map<?, ?> d = (Map<?, ?>) get(c, "D");
        assertEquals(EnumMap.class, d.getClass());
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), List.copyOf(d.keySet()));   // EnumMap 은 열거 순
        assertSame(Shapes.block(), d.get(Direction.SOUTH));
        Map<?, ?> e = (Map<?, ?>) get(c, "E");
        assertEquals(HashMap.class, e.getClass());
        assertSame(a, e.get(2));
        Map<?, ?> f = (Map<?, ?>) get(c, "F");
        assertEquals(List.of(Direction.WEST, Direction.EAST), List.copyOf(f.keySet()));   // 불변 지도는 넘어온 순서
        assertEquals("com.google.common.collect.RegularImmutableMap", f.getClass().getName());
        assertEquals("com.google.common.collect.ImmutableEnumMap", get(c, "G").getClass().getName());
        VoxelShape h = (VoxelShape) get(c, "H");
        assertEquals(0, h.furnaceBoxes().length);
        assertEquals(true, h.isEmpty());
        // 두 번째 클래스가 같은 번호를 쓰면 같은 객체(클래스 사이 별칭)
        Class<?> c2 = build("S5Shapes2", new String[][] {{"X", "[" + VS, "a 900002 1 E"}, {"Y", VS, "v 900003:0,0,0,1,1,1"}});
        assertSame(arr, get(c2, "X"));
        assertSame(arr[2], get(c2, "Y"));
    }

    @Test
    void badValuesStopTheGenerator() {
        assertThrows(IllegalArgumentException.class, () -> ShapeValue.parse("v 1:0,0,0,1,1"));   // 상자 좌표는 6개씩
        assertThrows(IllegalArgumentException.class, () -> ShapeValue.parse("a 1 2 E"));   // 개수가 안 맞음
        assertThrows(IllegalArgumentException.class, () -> ShapeValue.parse("m 1 enum i 1 3=E"));   // EnumMap 은 열거형 열쇠
        assertThrows(IllegalArgumentException.class, () -> ShapeValue.parse("m 1 tree i 1 3=E"));   // 모르는 지도 종류
        assertThrows(IllegalArgumentException.class, () -> ShapeValue.parse("q 1"));
    }
}
