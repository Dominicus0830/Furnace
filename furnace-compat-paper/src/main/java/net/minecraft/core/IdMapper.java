package net.minecraft.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * 값 ↔ 번호 표(Mojang 이름 shim, N2b-1) — 바닐라 1.21.1 포팅(정체성 비교, 없는 값은 -1). {@code Block.BLOCK_STATE_REGISTRY} 는 서버 상태 표를 그대로
 * 비추는 고정 표라 더하기(add·addMapping)를 막는다. 바닐라와 달리 {@code byId} 가 final 이 아니다(그 고정 표가 재정의한다).
 */
public class IdMapper<T> implements IdMap<T> {

    private int nextId;
    private final IdentityHashMap<T, Integer> tToId;
    private final List<T> idToT;

    public IdMapper() { this(512); }

    public IdMapper(int expectedSize) {
        this.idToT = new ArrayList<>(expectedSize);
        this.tToId = new IdentityHashMap<>(expectedSize);
    }

    public void addMapping(T value, int id) {
        tToId.put(value, id);
        while (idToT.size() <= id) idToT.add(null);
        idToT.set(id, value);
        if (nextId <= id) nextId = id + 1;
    }

    public void add(T value) { addMapping(value, nextId); }

    @Override
    public int getId(T value) {
        Integer id = tToId.get(value);
        return id == null ? -1 : id;
    }

    @Override
    public T byId(int id) { return id >= 0 && id < idToT.size() ? idToT.get(id) : null; }

    @Override
    public Iterator<T> iterator() { return idToT.stream().filter(Objects::nonNull).iterator(); }

    public boolean contains(int id) { return byId(id) != null; }

    @Override
    public int size() { return tToId.size(); }

    /** 호환 모듈 전용: 서버 표를 비추는 고정 표(번호·값 변환을 그때그때 묻는다). */
    public static <T> IdMapper<T> furnaceFixed(ToIntFunction<T> getId, IntFunction<T> byId, IntSupplier size) {
        return new IdMapper<>(0) {
            @Override
            public void addMapping(T value, int id) { throw new UnsupportedOperationException("Furnace 의 고정 번호 표에는 더할 수 없다"); }

            @Override
            public int getId(T value) { return value == null ? -1 : getId.applyAsInt(value); }

            @Override
            public T byId(int id) { return byId.apply(id); }

            @Override
            public Iterator<T> iterator() {
                int n = size.getAsInt();
                return java.util.stream.IntStream.range(0, n).mapToObj(byId).filter(Objects::nonNull).iterator();
            }

            @Override
            public int size() { return size.getAsInt(); }
        };
    }
}
