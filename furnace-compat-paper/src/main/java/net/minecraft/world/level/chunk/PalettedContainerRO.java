package net.minecraft.world.level.chunk;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** 읽기 전용 팔레트 컨테이너(Mojang 이름, N2a). 직렬화(write·pack·getSerializedSize)와 recreate(첫 팔레트 값으로 채움 — 팔레트 순서가 바닐라와 달라 같은 값을 못 낸다)는 없다. */
public interface PalettedContainerRO<T> {

    T get(int x, int y, int z);

    void getAll(Consumer<T> consumer);

    boolean maybeHas(Predicate<T> predicate);

    void count(PalettedContainer.CountConsumer<T> consumer);
}
