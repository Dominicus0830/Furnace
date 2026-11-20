package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;

/** 읽기 전용 팔레트 컨테이너(Mojang 이름). 코덱({@code PalettedContainer.codecRW}·{@code codecRO})이 쓰는 pack·PackedData·Unpacker 도 바닐라 모양. */
public interface PalettedContainerRO<T> {

    T get(int x, int y, int z);

    void getAll(Consumer<T> consumer);

    void write(FriendlyByteBuf buf);

    int getSerializedSize();

    boolean maybeHas(Predicate<T> predicate);

    void count(PalettedContainer.CountConsumer<T> consumer);

    PalettedContainer<T> recreate();

    /** 바닐라: 저장 꼴로 묶는다 — 실제로 쓰인 값만 든 팔레트(처음 나온 순서)와, 그 크기에 맞춘 비트 수의 long 배열(0 비트면 없음). */
    PackedData<T> pack(net.minecraft.core.IdMap<T> registry, PalettedContainer.Strategy strategy);

    /** 바닐라 레코드: 저장 꼴(팔레트 항목, long 배열). */
    record PackedData<T>(List<T> paletteEntries, java.util.Optional<java.util.stream.LongStream> storage) {}

    /** 바닐라: 저장 꼴에서 컨테이너를 만든다. */
    @FunctionalInterface
    interface Unpacker<T, C extends PalettedContainerRO<T>> {
        com.mojang.serialization.DataResult<C> read(net.minecraft.core.IdMap<T> registry, PalettedContainer.Strategy strategy, PackedData<T> serialized);
    }
}
