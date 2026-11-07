// 바닐라 EntityType.createDefaultStackConfig / updateCustomEntityTag / SpawnEggItem.getType: 아이템으로 엔티티를 놓을 때
// 아이템의 이름(custom_name)과 엔티티 데이터(entity_data)를 새 엔티티에 입힌다. 스폰 알·몹 양동이·배·수레(놓기·발사기)가 쓴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class EntityStackConfig {
    private EntityStackConfig() {}

    public static final String ENTITY_DATA = "minecraft:entity_data";

    /**
     * SpawnEggItem.getType: entity_data 에 등록된 엔티티 종류 id 가 있으면 그것(알의 기본 종류를 덮는다), 없거나 모르는 종류면 기본.
     * (ENTITY_TYPE_FIELD_CODEC 이 읽지 못하면 기본 종류로 돌아간다.)
     */
    public static String eggType(ItemStack egg, String defaultType) {
        if (ItemTools.component(egg, ENTITY_DATA) instanceof NbtCompound c && !c.tags().isEmpty() && c.tags().get("id") instanceof String id
                && ItemContext.installed() && ItemContext.id("minecraft:entity_type", id) != null) {
            return id.indexOf(':') < 0 ? "minecraft:" + id : id;
        }
        return defaultType;
    }

    /**
     * createDefaultStackConfig(level, stack, player).accept(entity): 아이템의 custom_name 을 이름으로, entity_data 를 엔티티 NBT 위에 합쳐 읽는다.
     * 순서는 바닐라와 같다 — 이름 먼저, 그다음 데이터(데이터에 CustomName 이 있으면 그쪽이 이긴다). player 가 null 이면 발사기.
     */
    public static void apply(ChunkLevel level, ItemStack stack, BlockActor player, Entity entity) {
        if (stack == null || stack.isEmpty() || entity == null) return;
        if (ItemTools.component(stack, "minecraft:custom_name") instanceof String json) {
            try {
                entity.setCustomName(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json));
            } catch (RuntimeException ignored) {
                // 읽을 수 없는 이름은 이름 없이
            }
        }
        if (ItemTools.component(stack, ENTITY_DATA) instanceof NbtCompound data && !data.tags().isEmpty()) updateCustomEntityTag(player, entity, data);
    }

    /**
     * EntityType.updateCustomEntityTag: 엔티티를 저장한 NBT 에 데이터를 합쳐(compound 는 안으로) 다시 읽고, UUID 는 그대로 둔다.
     * 관리자만 바꿀 수 있는 종류(명령 블록 수레)는 관리자 플레이어가 놓을 때만 — 발사기(null)는 못 한다.
     */
    public static void updateCustomEntityTag(BlockActor player, Entity entity, NbtCompound data) {
        if (entity.onlyOpCanSetNbt() && (player == null || player.permissionLevel() <= 0)) return;
        NbtCompound tag = entity.saveWithoutId(new NbtCompound());
        java.util.UUID uuid = entity.uuid();
        NbtTags.merge(tag, (NbtCompound) NbtTags.copy(data));
        entity.load(tag);
        entity.setUuid(uuid);
    }
}
