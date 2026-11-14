package net.minecraft.world.level.chunk;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;

/** 읽기 전용 팔레트 컨테이너(Mojang 이름). 코덱용 pack·PackedData·Unpacker 는 DataFixerUpper shim 이 없어 두지 않았다. */
public interface PalettedContainerRO<T> {

    T get(int x, int y, int z);

    void getAll(Consumer<T> consumer);

    void write(FriendlyByteBuf buf);

    int getSerializedSize();

    boolean maybeHas(Predicate<T> predicate);

    void count(PalettedContainer.CountConsumer<T> consumer);

    PalettedContainer<T> recreate();
}
