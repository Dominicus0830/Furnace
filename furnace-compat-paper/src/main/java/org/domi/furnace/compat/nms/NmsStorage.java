package org.domi.furnace.compat.nms;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtBridge;
import org.domi.furnace.api.internal.ChunkInternals;

/** 디스크에 저장된 청크 NBT 읽기(NMS shim 5e — 바닐라 ChunkStorage#read, Paper RegionFileIOThread). 종류 번호는 {@link ChunkInternals#STORED_CHUNK} 차례. */
public final class NmsStorage {

    private NmsStorage() {}

    /**
     * 그 레벨의 저장된 NBT(없으면 null). 읽기 실패는 원인(IOException 등)을 담은 CompletionException 으로 끝난다. 기다리지 않는다.
     * 결과는 서버 비동기 일꾼({@link org.domi.furnace.api.Furnace#asyncExecutor()})에서 낸다 — 뒤에 붙는 플러그인 콜백이 저장도 맡는 IO 차례를 쥐지 않게
     * (느린 콜백이 저장을 밀거나, 콜백 안에서 같은 차례의 읽기를 기다려 교착하지 않게).
     */
    public static CompletableFuture<CompoundTag> read(Object level, int kind, int chunkX, int chunkZ, String what) {
        var w = NmsEffects.world(level, what);
        return NmsChunks.bridge().storedNbt(w, kind, chunkX, chunkZ).handleAsync((t, e) -> {
            if (e != null) throw new CompletionException(unwrap(e));
            return t == null ? null : (CompoundTag) NbtBridge.fromFurnace(t);
        }, org.domi.furnace.api.Furnace.server().asyncExecutor());
    }

    /** CompletionException·UncheckedIOException 껍질을 벗긴 원인. */
    public static Throwable unwrap(Throwable e) {
        while ((e instanceof CompletionException || e instanceof java.io.UncheckedIOException) && e.getCause() != null) e = e.getCause();
        return e;
    }
}
