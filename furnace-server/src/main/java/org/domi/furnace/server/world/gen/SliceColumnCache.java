// 셀 꼭짓점 기둥 공유 캐시: 이웃 청크가 같은 꼭짓점 기둥(x, z 고정, y 전체)의 보간기 표본을 다시 계산하지 않게 한다(성능).
package org.domi.furnace.server.world.gen;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * 바닐라 NoiseChunk 는 청크마다 (cellCountXZ+1)² 개 꼭짓점 기둥에서 보간기({@code interpolated}) 함수를 계산한다. 청크 경계의
 * 기둥(5×5 중 9개)은 이웃 청크와 같은 월드 좌표라 바닐라는 같은 값을 두 번 계산한다. 꼭짓점 값은 라우터(시드·노이즈 설정)와
 * 월드 셀 좌표만의 함수다 — 보간기 안의 캐시 노드(flat_cache 는 청크 쿼트 표를 미리 같은 식으로 채운 것, cache_2d·cache_once 는
 * 같은 점의 메모)는 값을 바꾸지 않는다. 그래서 같은 라우터·같은 보간기 구성(layout)이면 한 번 계산한 기둥을 그대로 써도 결과 비트가 같다.
 *
 * 잠금 없는 직접 사상 표: 칸마다 불변 항목(좌표·구성·값 배열)을 하나 둔다. 충돌하면 덮어쓸 뿐이고, 읽을 때 좌표·구성이 다르면
 * 없는 것으로 본다 — 틀린 값을 돌려주지 않는다. 항목의 값 배열은 발행 뒤 고치지 않는다(final 필드로 안전하게 발행).
 */
public final class SliceColumnCache {

    /** -Dfurnace.worldgen.sliceCache=false 로 끈다(전후 비교·문제 분리용). */
    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("furnace.worldgen.sliceCache"));

    private record Column(int cellX, int cellZ, long layout, double[] values) {}

    private final AtomicReferenceArray<Column> table;
    private final int mask;
    final LongAdder hits = new LongAdder();
    final LongAdder misses = new LongAdder();

    /** 구성별 판정: 보간기 함수 안에 상태를 읽는 노드(다른 보간기·cache_all_in_cell)가 있으면 꼭짓점 값이 위치만의 함수가 아니라 쓰지 않는다. */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> layoutSafe = new java.util.concurrent.ConcurrentHashMap<>();

    boolean safeFor(long layout, java.util.function.BooleanSupplier check) {
        return layoutSafe.computeIfAbsent(layout, k -> check.getAsBoolean());
    }

    /** @param sizeLog2 칸 수의 log2(칸마다 보간기 수 × (cellCountY+1) 개 double) */
    SliceColumnCache(int sizeLog2) {
        this.table = new AtomicReferenceArray<>(1 << sizeLog2);
        this.mask = (1 << sizeLog2) - 1;
    }

    private int slot(int cellX, int cellZ) {
        int h = cellX * 0x9E3779B1 + cellZ * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 13;
        return h & mask;
    }

    /** 저장된 기둥 값(보간기 순서대로 이어 붙인 배열, 읽기 전용) 또는 null. */
    double[] get(int cellX, int cellZ, long layout) {
        Column c = table.get(slot(cellX, cellZ));
        if (c != null && c.cellX == cellX && c.cellZ == cellZ && c.layout == layout) {
            hits.increment();
            return c.values;
        }
        misses.increment();
        return null;
    }

    /** values 는 넘긴 뒤 고치지 않는다. */
    void put(int cellX, int cellZ, long layout, double[] values) {
        table.set(slot(cellX, cellZ), new Column(cellX, cellZ, layout, values));
    }
}
