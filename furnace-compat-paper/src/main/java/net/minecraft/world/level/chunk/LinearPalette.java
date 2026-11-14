package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

/** 배열 팔레트(Mojang 이름 shim, N2b-1, 바닐라 1.21.1 포팅) — 2^bits 칸까지 넣은 순서로 번호를 매기고(== 비교), 넘치면 bits+1 로 늘려 달라고 한다. */
public class LinearPalette<T> implements Palette<T> {

    private final IdMap<T> registry;
    private final T[] values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;
    private int size;

    @SuppressWarnings("unchecked")
    private LinearPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, List<T> initial) {
        this.registry = registry;
        this.values = (T[]) new Object[1 << bits];
        this.bits = bits;
        this.resizeHandler = resizeHandler;
        if (initial.size() > values.length) {
            throw new IllegalArgumentException(String.format("Can't initialize LinearPalette of size %d with %d entries", values.length, initial.size()));
        }
        for (int i = 0; i < initial.size(); i++) values[i] = initial.get(i);
        this.size = initial.size();
    }

    private LinearPalette(IdMap<T> registry, T[] values, PaletteResize<T> resizeHandler, int bits, int size) {
        this.registry = registry;
        this.values = values;
        this.resizeHandler = resizeHandler;
        this.bits = bits;
        this.size = size;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new LinearPalette<>(registry, bits, resizeHandler, values);
    }

    @Override
    public int idFor(T v) {
        for (int i = 0; i < size; i++) if (values[i] == v) return i;
        int i = size;
        if (i < values.length) {
            values[i] = v;
            size++;
            return i;
        }
        return resizeHandler.onResize(bits + 1, v);
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for (int i = 0; i < size; i++) if (predicate.test(values[i])) return true;
        return false;
    }

    @Override
    public T valueFor(int id) {
        if (id >= 0 && id < size) return values[id];
        throw new MissingPaletteEntryException(id);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        size = buf.readVarInt();
        for (int i = 0; i < size; i++) values[i] = registry.byIdOrThrow(buf.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) buf.writeVarInt(registry.getId(values[i]));
    }

    @Override
    public int getSerializedSize() {
        int n = FriendlyByteBuf.getVarIntSize(getSize());
        for (int i = 0; i < getSize(); i++) n += FriendlyByteBuf.getVarIntSize(registry.getId(values[i]));
        return n;
    }

    @Override
    public int getSize() { return size; }

    @Override
    public Palette<T> copy() { return new LinearPalette<>(registry, values.clone(), resizeHandler, bits, size); }
}
