package org.domi.furnace.compat.nms;

import java.lang.invoke.VarHandle;

/**
 * 쓰기 장벽: 플러그인 바이트코드의 참조 배열 VarHandle 쓰기(N2b-4, 설계 3.3-3). {@link NmsClassTransformer} 가 {@code VarHandle} 의 배열 칸 쓰기 호출 중
 * 서술자가 {@code ([Ljava/lang/Object;I...)} 인 것을 여기로 돌린다 — NMS 이름 힌트 게이트 <b>밖</b>이다(FAWE 의 섹션 CAS 는 "minecraft" 문자열이 없는
 * worldedit-core {@code ReflectionUtils.compareAndSet} 에 있다). 원래 연산을 그대로 한 뒤, 그 배열이 로드된 청크 shim 의 섹션 배열이면 거울에 알린다
 * (주인이 다음 맞춤 지점에서 들여온다). 정확성은 틱 끝 훑기가 보장하고 이 장벽은 지연을 줄인다. 비용은 바뀐 호출 자리에서 형 검사 하나.
 * <p>서술자가 NMS 원소 형({@code ([Lnet/minecraft/...;I...)})인 호출도 {@link #normalize} 로 Object 꼴로 바꿔 돌린다(VarHandle 은 invoke 의미라 형이
 * 맞춰진다. {@code withInvokeExactBehavior()} 핸들은 제외할 수 없어 그 꼴만 NMS 원소 형으로 좁혔다).
 */
public final class NmsArrays {

    private NmsArrays() {}

    /** 변환기가 돌리는 접근 모드와 서술자. */
    static boolean rewrites(String name, String desc) {
        return switch (name) {
            case "compareAndSet", "weakCompareAndSet", "weakCompareAndSetPlain", "weakCompareAndSetAcquire", "weakCompareAndSetRelease" ->
                    desc.equals("([Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Z");
            case "compareAndExchange", "compareAndExchangeAcquire", "compareAndExchangeRelease" ->
                    desc.equals("([Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            case "set", "setVolatile", "setRelease", "setOpaque" -> desc.equals("([Ljava/lang/Object;ILjava/lang/Object;)V");
            case "getAndSet", "getAndSetAcquire", "getAndSetRelease" -> desc.equals("([Ljava/lang/Object;ILjava/lang/Object;)Ljava/lang/Object;");
            default -> false;
        };
    }

    private static final String OBJ = "Ljava/lang/Object;";

    /**
     * VarHandle 배열 칸 접근 서술자를 Object 꼴로: 첫 인자가 참조 배열, 둘째가 int, 나머지가 참조인 꼴만. Object 원소 꼴은 그대로,
     * 원소 형이 NMS({@code net/minecraft/})인 꼴은 Object 로 바꾼 서술자, 그 밖은 null(바꾸지 않음).
     */
    static String normalize(String desc) {
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(desc);
        org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(desc);
        if (args.length < 3 || args[0].getSort() != org.objectweb.asm.Type.ARRAY || args[1].getSort() != org.objectweb.asm.Type.INT) return null;
        if (args[0].getDimensions() != 1) return null;
        org.objectweb.asm.Type elem = args[0].getElementType();
        if (elem.getSort() != org.objectweb.asm.Type.OBJECT) return null;
        boolean object = elem.getInternalName().equals("java/lang/Object");
        if (!object && !elem.getInternalName().startsWith("net/minecraft/")) return null;
        StringBuilder b = new StringBuilder("([Ljava/lang/Object;I");
        for (int i = 2; i < args.length; i++) {
            int sort = args[i].getSort();
            if (sort != org.objectweb.asm.Type.OBJECT && sort != org.objectweb.asm.Type.ARRAY) return null;
            b.append(OBJ);
        }
        b.append(')');
        switch (ret.getSort()) {
            case org.objectweb.asm.Type.VOID, org.objectweb.asm.Type.BOOLEAN -> b.append(ret.getDescriptor());
            case org.objectweb.asm.Type.OBJECT, org.objectweb.asm.Type.ARRAY -> b.append(OBJ);
            default -> {
                return null;
            }
        }
        return b.toString();
    }

    private static void written(Object[] array) {
        if (array instanceof net.minecraft.world.level.chunk.LevelChunkSection[]) {
            try {
                net.minecraft.world.level.chunk.ChunkAccess.furnaceBarrierArrayWritten(array);
            } catch (RuntimeException | LinkageError ignored) {
                // 알림은 최적화다 — 틱 끝 훑기가 잡는다
            }
        }
    }

    public static boolean compareAndSet(VarHandle vh, Object[] a, int i, Object e, Object v) {
        boolean r = vh.compareAndSet(a, i, e, v);
        if (r) written(a);
        return r;
    }

    public static boolean weakCompareAndSet(VarHandle vh, Object[] a, int i, Object e, Object v) {
        boolean r = vh.weakCompareAndSet(a, i, e, v);
        if (r) written(a);
        return r;
    }

    public static boolean weakCompareAndSetPlain(VarHandle vh, Object[] a, int i, Object e, Object v) {
        boolean r = vh.weakCompareAndSetPlain(a, i, e, v);
        if (r) written(a);
        return r;
    }

    public static boolean weakCompareAndSetAcquire(VarHandle vh, Object[] a, int i, Object e, Object v) {
        boolean r = vh.weakCompareAndSetAcquire(a, i, e, v);
        if (r) written(a);
        return r;
    }

    public static boolean weakCompareAndSetRelease(VarHandle vh, Object[] a, int i, Object e, Object v) {
        boolean r = vh.weakCompareAndSetRelease(a, i, e, v);
        if (r) written(a);
        return r;
    }

    public static Object compareAndExchange(VarHandle vh, Object[] a, int i, Object e, Object v) {
        Object r = vh.compareAndExchange(a, i, e, v);
        if (r == e) written(a);
        return r;
    }

    public static Object compareAndExchangeAcquire(VarHandle vh, Object[] a, int i, Object e, Object v) {
        Object r = vh.compareAndExchangeAcquire(a, i, e, v);
        if (r == e) written(a);
        return r;
    }

    public static Object compareAndExchangeRelease(VarHandle vh, Object[] a, int i, Object e, Object v) {
        Object r = vh.compareAndExchangeRelease(a, i, e, v);
        if (r == e) written(a);
        return r;
    }

    public static void set(VarHandle vh, Object[] a, int i, Object v) {
        vh.set(a, i, v);
        written(a);
    }

    public static void setVolatile(VarHandle vh, Object[] a, int i, Object v) {
        vh.setVolatile(a, i, v);
        written(a);
    }

    public static void setRelease(VarHandle vh, Object[] a, int i, Object v) {
        vh.setRelease(a, i, v);
        written(a);
    }

    public static void setOpaque(VarHandle vh, Object[] a, int i, Object v) {
        vh.setOpaque(a, i, v);
        written(a);
    }

    public static Object getAndSet(VarHandle vh, Object[] a, int i, Object v) {
        Object r = vh.getAndSet(a, i, v);
        written(a);
        return r;
    }

    public static Object getAndSetAcquire(VarHandle vh, Object[] a, int i, Object v) {
        Object r = vh.getAndSetAcquire(a, i, v);
        written(a);
        return r;
    }

    public static Object getAndSetRelease(VarHandle vh, Object[] a, int i, Object v) {
        Object r = vh.getAndSetRelease(a, i, v);
        written(a);
        return r;
    }
}
