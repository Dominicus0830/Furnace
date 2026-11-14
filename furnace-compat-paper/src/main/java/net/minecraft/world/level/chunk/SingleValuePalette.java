package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

/** 값 하나 팔레트(Mojang 이름 shim, N2b-1, 바닐라 1.21.1 포팅). 다른 값이 오면 1비트 팔레트로 늘려 달라고 한다. */
public class SingleValuePalette<T> implements Palette<T> {

    private final IdMap<T> registry;
    private T value;
    private final PaletteResize<T> resizeHandler;

    public SingleValuePalette(IdMap<T> registry, PaletteResize<T> resizeHandler, List<T> values) {
        this.registry = registry;
        this.resizeHandler = resizeHandler;
        if (values.size() > 0) {
            if (values.size() > 1) throw new IllegalArgumentException(String.format("Can't initialize SingleValuePalette with %d values.", (long) values.size()));
            this.value = values.get(0);
        }
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new SingleValuePalette<>(registry, resizeHandler, values);
    }

    @Override
    public int idFor(T v) {
        if (value != null && value != v) return resizeHandler.onResize(1, v);
        value = v;
        return 0;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        if (value == null) throw new IllegalStateException("Use of an uninitialized palette");
        return predicate.test(value);
    }

    @Override
    public T valueFor(int id) {
        if (value != null && id == 0) return value;
        throw new IllegalStateException("Missing Palette entry for id " + id + ".");
    }

    @Override
    public void read(FriendlyByteBuf buf) { value = registry.byIdOrThrow(buf.readVarInt()); }

    @Override
    public void write(FriendlyByteBuf buf) {
        if (value == null) throw new IllegalStateException("Use of an uninitialized palette");
        buf.writeVarInt(registry.getId(value));
    }

    @Override
    public int getSerializedSize() {
        if (value == null) throw new IllegalStateException("Use of an uninitialized palette");
        return FriendlyByteBuf.getVarIntSize(registry.getId(value));
    }

    @Override
    public int getSize() { return 1; }

    /** 바닐라: 값이 하나라 사본 대신 자기 자신. */
    @Override
    public Palette<T> copy() {
        if (value == null) throw new IllegalStateException("Use of an uninitialized palette");
        return this;
    }
}
