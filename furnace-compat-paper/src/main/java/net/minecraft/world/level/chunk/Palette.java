package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

/** 칸 번호 ↔ 값 표(Mojang 이름 shim, N2b-1). 바닐라 1.21.1 의 네 구현: {@link SingleValuePalette}·{@link LinearPalette}·{@link HashMapPalette}·{@link GlobalPalette}. */
public interface Palette<T> {

    int idFor(T value);

    boolean maybeHas(Predicate<T> predicate);

    T valueFor(int id);

    void read(FriendlyByteBuf buf);

    void write(FriendlyByteBuf buf);

    int getSerializedSize();

    int getSize();

    Palette<T> copy();

    interface Factory {
        <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values);
    }
}
