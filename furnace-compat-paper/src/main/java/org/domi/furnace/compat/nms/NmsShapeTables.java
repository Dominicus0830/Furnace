package org.domi.furnace.compat.nms;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * T-263 S5 모양 정적 표. 생성기가 바닐라 블록 클래스의 모양 정적 필드(VoxelShape·배열·지도)를 클래스 초기화에서 이 도우미로 채운다.
 * 값(상자 좌표·지도 열쇠)은 빌드 때 바닐라를 띄워 읽은 채움 표에서 오고, 번호는 바닐라의 객체 하나마다 하나다 — 같은 번호는 같은 객체를
 * 돌려줘 바닐라의 별칭(필드 사이·배열 칸 사이·다른 클래스 사이)을 그대로 지킨다. 클래스 초기화는 여러 스레드에서 일어날 수 있어 표는
 * 동시 지도이고, 먼저 만든 것이 이긴다.
 */
public final class NmsShapeTables {

    private static final Map<Integer, Object> BY_ID = new ConcurrentHashMap<>();

    private NmsShapeTables() {}

    /** 번호 id 의 모양. nums 는 상자마다 minX,minY,minZ,maxX,maxY,maxZ(쉼표 구분, 빈 글이면 상자 없음). */
    public static VoxelShape shape(int id, String nums) {
        return (VoxelShape) BY_ID.computeIfAbsent(id, k -> VoxelShape.furnaceOf(parse(nums)));
    }

    /** 번호 id 의 모양 배열(처음 넘어온 배열을 그대로 쓴다). */
    public static VoxelShape[] array(int id, VoxelShape[] elements) {
        return (VoxelShape[]) BY_ID.computeIfAbsent(id, k -> elements);
    }

    /**
     * 번호 id 의 모양 지도. kind: {@code enum}(EnumMap) · {@code ienum}(Guava 불변 열거형 지도) · {@code imm}(Guava 불변 지도, 넘어온 순서) ·
     * {@code hash}(HashMap). keys·values 는 바닐라 지도를 돌 때의 순서다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<?, VoxelShape> map(int id, String kind, Object[] keys, VoxelShape[] values) {
        return (Map<?, VoxelShape>) BY_ID.computeIfAbsent(id, k -> {
            if (keys.length != values.length || keys.length == 0) throw new IllegalArgumentException("shape table map " + id + ": keys and values differ");
            LinkedHashMap ordered = new LinkedHashMap();
            for (int i = 0; i < keys.length; i++) ordered.put(keys[i], values[i]);
            return switch (kind) {
                case "enum" -> {
                    EnumMap m = new EnumMap(((Enum) keys[0]).getDeclaringClass());
                    m.putAll(ordered);
                    yield m;
                }
                case "ienum" -> Maps.immutableEnumMap(ordered);
                case "imm" -> ImmutableMap.copyOf(ordered);
                case "hash" -> new HashMap(ordered);
                default -> throw new IllegalArgumentException("shape table map " + id + ": unknown kind " + kind);
            };
        });
    }

    private static double[] parse(String nums) {
        if (nums.isEmpty()) return new double[0];
        String[] p = nums.split(",");
        if (p.length % 6 != 0) throw new IllegalArgumentException("shape table: box coordinates come in sixes");
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Double.parseDouble(p[i]);
        return out;
    }
}
