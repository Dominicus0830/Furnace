package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

/** 해시 팔레트(Mojang 이름 shim, N2b-1, 바닐라 1.21.1 포팅) — 넣은 순서로 번호, 2^bits 를 넘으면 bits+1 로 늘려 달라고 한다. */
public class HashMapPalette<T> implements Palette<T> {

    private final IdMap<T> registry;
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, List<T> initial) {
        this(registry, bits, resizeHandler);
        initial.forEach(values::add);
    }

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler) {
        this(registry, bits, resizeHandler, CrudeIncrementalIntIdentityHashBiMap.create(1 << bits));
    }

    private HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, CrudeIncrementalIntIdentityHashBiMap<T> values) {
        this.registry = registry;
        this.bits = bits;
        this.resizeHandler = resizeHandler;
        this.values = values;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new HashMapPalette<>(registry, bits, resizeHandler, values);
    }

    @Override
    public int idFor(T v) {
        int id = values.getId(v);
        if (id == -1) {
            id = values.add(v);
            if (id >= 1 << bits) id = resizeHandler.onResize(bits + 1, v);
        }
        return id;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for (int i = 0; i < getSize(); i++) if (predicate.test(values.byId(i))) return true;
        return false;
    }

    @Override
    public T valueFor(int id) {
        T v = values.byId(id);
        if (v == null) throw new MissingPaletteEntryException(id);
        return v;
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        values.clear();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) values.add(registry.byIdOrThrow(buf.readVarInt()));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        int n = getSize();
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeVarInt(registry.getId(values.byId(i)));
    }

    @Override
    public int getSerializedSize() {
        int n = FriendlyByteBuf.getVarIntSize(getSize());
        for (int i = 0; i < getSize(); i++) n += FriendlyByteBuf.getVarIntSize(registry.getId(values.byId(i)));
        return n;
    }

    public List<T> getEntries() {
        List<T> out = new ArrayList<>();
        values.iterator().forEachRemaining(out::add);
        return out;
    }

    @Override
    public int getSize() { return values.size(); }

    @Override
    public Palette<T> copy() { return new HashMapPalette<>(registry, bits, resizeHandler, values.copy()); }
}
