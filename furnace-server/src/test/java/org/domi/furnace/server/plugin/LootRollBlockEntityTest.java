// 루트 다리의 block_entity 매개변수: 저장 꼴만으로 떨어진 블록 엔티티를 만들어 collectComponents 를 얻는다(월드·스레드 없이).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.junit.jupiter.api.Test;

class LootRollBlockEntityTest {

    @Test
    void detachedBlockEntityKeepsContentsAndName() {
        var box = new ShulkerBoxBlockEntity(new Pos(3, 64, 3), -1);
        box.items[0] = new ItemStack(ItemContext.id("minecraft:item", "minecraft:diamond"), 5);
        box.customName = "\"Loot\"";
        var api = ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) NbtTags.thaw(NbtTags.copy(box.saveWithFullMetadata())));
        var detached = ChunkInternalsImpl.detachedBlockEntity(B.defaultState("minecraft:shulker_box"), new Pos(0, 0, 0), api);
        assertNotNull(detached);
        var components = detached.collectComponents();
        assertEquals("\"Loot\"", components.get("minecraft:custom_name"));
        assertNotNull(components.get("minecraft:container"));
    }
}
