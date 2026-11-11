package net.minecraft.core;

/** 번호 ↔ 값 표(Mojang 이름 shim, N0). */
public interface IdMap<T> extends Iterable<T> {

    int DEFAULT = -1;

    int getId(T value);

    T byId(int id);

    default T byIdOrThrow(int id) {
        T v = byId(id);
        if (v == null) throw new IllegalArgumentException("No value with id " + id);
        return v;
    }

    default int getIdOrThrow(T value) {
        int id = getId(value);
        if (id == -1) throw new IllegalArgumentException("Can't find id for '" + value + "' in map " + this);
        return id;
    }

    int size();
}
