package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

/** 레지스트리 번호를 그대로 쓰는 팔레트(Mojang 이름 shim, N2b-1, 바닐라 1.21.1 포팅). 모르는 값은 0, maybeHas 는 늘 true. */
public class GlobalPalette<T> implements Palette<T> {

    private final IdMap<T> registry;

    public GlobalPalette(IdMap<T> registry) { this.registry = registry; }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new GlobalPalette<>(registry);
    }

    @Override
    public int idFor(T v) {
        int id = registry.getId(v);
        return id == -1 ? 0 : id;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) { return true; }

    @Override
    public T valueFor(int id) {
        T v = registry.byId(id);
        if (v == null) throw new MissingPaletteEntryException(id);
        return v;
    }

    @Override
    public void read(FriendlyByteBuf buf) {}

    @Override
    public void write(FriendlyByteBuf buf) {}

    @Override
    public int getSerializedSize() { return 0; }

    @Override
    public int getSize() { return registry.size(); }

    @Override
    public Palette<T> copy() { return this; }
}
