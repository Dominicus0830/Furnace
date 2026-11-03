package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerPendingChunksTest {

    @Test
    void pendingChunks_preserveInsertionOrder() {
        Player player = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());

        assertEquals(LinkedHashSet.class, player.pendingChunks().getClass());
    }
}
