// World#getChunkAtAsync 콜백판의 Future 잇기(Paper: thenAccept(cb).exceptionally(경고 한 줄)).
package org.domi.furnace.compat.paper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

public final class ChunkCallbacks {

    private ChunkCallbacks() {}

    public static void run(CompletableFuture<Chunk> f, Consumer<? super Chunk> cb) {
        f.thenAccept(cb).exceptionally(ex -> {
            Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Exception in chunk load callback", ex);
            return null;
        });
    }
}
