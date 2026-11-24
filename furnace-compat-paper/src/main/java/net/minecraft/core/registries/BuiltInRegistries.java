package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.domi.furnace.compat.nms.NmsFluids;
import org.domi.furnace.compat.nms.NmsBlocks;
import org.domi.furnace.compat.nms.NmsItems;
import org.domi.furnace.compat.nms.NmsMapDecorations;
import org.domi.furnace.compat.nms.NmsMobEffects;
import org.domi.furnace.compat.nms.NmsSounds;

/**
 * 내장 레지스트리(Mojang 이름 shim, N0). 지금은 블록·아이템·유체(바닐라 DefaultedMappedRegistry 규칙, 기본값 minecraft:air, 유체는 minecraft:empty)와
 * 소리·상태 효과(바닐라 MappedRegistry 규칙, 기본값 없음 — easy-nms E4c-1b)·지도 표시 종류(E4c-2)·주민 종류·직업·고양이/개구리 무늬(엔티티 데이터 D4)·입자 종류만.
 * 처음 읽을 때 서버 블록 표에서 만들므로 서버가 떠 있어야 한다. 다른 레지스트리는 없는 필드다(호출 위치에서 실패).
 */
public final class BuiltInRegistries {

    private BuiltInRegistries() {}

    public static final DefaultedRegistry<Block> BLOCK = NmsBlocks.registry();
    public static final DefaultedRegistry<Item> ITEM = NmsItems.registry();
    public static final DefaultedRegistry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE = org.domi.furnace.compat.nms.NmsEntityTypes.registry();
    public static final DefaultedRegistry<Fluid> FLUID = NmsFluids.registry();
    /** 청크 생성 단계(바닐라 DefaultedMappedRegistry, 기본값 minecraft:empty). */
    public static final DefaultedRegistry<net.minecraft.world.level.chunk.status.ChunkStatus> CHUNK_STATUS = org.domi.furnace.compat.nms.NmsChunkStatuses.registry();
    public static final Registry<SoundEvent> SOUND_EVENT = NmsSounds.registry();
    public static final Registry<MobEffect> MOB_EFFECT = NmsMobEffects.registry();
    public static final Registry<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTE = org.domi.furnace.compat.nms.NmsAttributes.registry();
    public static final Registry<net.minecraft.core.component.DataComponentType<?>> DATA_COMPONENT_TYPE = org.domi.furnace.compat.nms.NmsDataComponentTypes.registry();
    public static final Registry<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPE = org.domi.furnace.compat.nms.NmsBlockEntityTypes.registry();
    public static final Registry<MapDecorationType> MAP_DECORATION_TYPE = NmsMapDecorations.registry();
    /** 고양이·개구리 무늬(바닐라 registerSimple, 기본값 없음 — 엔티티 데이터 설계 D4). */
    public static final Registry<net.minecraft.world.entity.animal.CatVariant> CAT_VARIANT = org.domi.furnace.compat.nms.NmsVariants.cats();
    public static final Registry<net.minecraft.world.entity.animal.FrogVariant> FROG_VARIANT = org.domi.furnace.compat.nms.NmsVariants.frogs();
    /** 주민 종류·직업(바닐라 DefaultedMappedRegistry, 기본값 minecraft:plains·minecraft:none — 엔티티 데이터 설계 D4). */
    public static final DefaultedRegistry<net.minecraft.world.entity.npc.VillagerType> VILLAGER_TYPE = org.domi.furnace.compat.nms.NmsVillagers.typeRegistry();
    public static final DefaultedRegistry<net.minecraft.world.entity.npc.VillagerProfession> VILLAGER_PROFESSION = org.domi.furnace.compat.nms.NmsVillagers.professionRegistry();
    /** 입자 종류(바닐라 registerSimple, 기본값 없음 — 엔티티 데이터 칸 particle·particles). */
    public static final Registry<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPE = org.domi.furnace.compat.nms.NmsParticles.registry();
    public static final Registry<net.minecraft.world.entity.ai.village.poi.PoiType> POINT_OF_INTEREST_TYPE = org.domi.furnace.compat.nms.NmsPoiTypes.registry();
}
