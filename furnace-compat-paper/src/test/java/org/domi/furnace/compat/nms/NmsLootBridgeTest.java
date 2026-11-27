package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 루트 다리 끝에서 끝까지: 시험 스레드(어느 region 도 아님)에서 LootTable#getRandomItems 를 부른다 — 위치 라우팅·시드·동적 드롭·피해 원인·블록 개체가 다리를 지난다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsLootBridgeTest {

    FurnaceTestServer server;
    ServerLevel level;
    Vec3 origin;

    /** damage_source 값의 모양(typeHolder·getEntity·getDirectEntity) — DamageSource 형이 호환 계층에 생기기 전의 읽기 경로를 부른다. */
    public static class FakeDamageSource {
        public net.minecraft.core.Holder<?> typeHolder() { return net.minecraft.core.Holder.direct("minecraft:player_attack"); }
        public net.minecraft.world.entity.Entity getEntity() { return null; }
        public net.minecraft.world.entity.Entity getDirectEntity() { return null; }
    }

    @BeforeAll
    void start() throws Exception {
        java.nio.file.Path vanilla = java.nio.file.Path.of("../tools/vanilla-data/server-" + org.domi.furnace.api.GameVersion.MINECRAFT);
        Assumptions.assumeTrue(java.nio.file.Files.isDirectory(vanilla.resolve("data/minecraft/loot_table")), "로컬 바닐라 데이터 없음");
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Looter");
        server.tick(3);
        var loc = tp.player().location();
        int cx = Math.floorDiv((int) Math.floor(loc.x()), 16), cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        server.loadChunk(cx, cz);
        server.tick(2);
        var tmp = java.nio.file.Files.createTempDirectory("loot-bridge");
        var ld = org.domi.furnace.server.world.storage.LevelData.create("world", 1234L, true, false, org.domi.furnace.server.world.GameMode.SURVIVAL, false,
                org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of());
        server.internals().dataPacks(org.domi.furnace.server.data.DataPackManager.start(vanilla, tmp, ld,
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt"), true, List.of("vanilla"), List.of()));
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        origin = new Vec3(cx * 16 + 8.5, loc.y(), cz * 16 + 8.5);
    }

    @AfterAll
    void stop() { if (server != null) server.close(); }

    /** damage_source·block_entity 를 허용하는 시험용 집합(필수는 origin 뿐). */
    static final net.minecraft.world.level.storage.loot.parameters.LootContextParamSet WITH_DAMAGE = net.minecraft.world.level.storage.loot.parameters.LootContextParamSet.builder()
            .required(LootContextParams.ORIGIN).optional(LootContextParams.DAMAGE_SOURCE).optional(LootContextParams.BLOCK_ENTITY).build();

    private LootParams.Builder chest() {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin);
    }

    @Test
    void positionOnlyRollWithSeedIsDeterministic() {
        LootTable t = LootTable.furnaceOf("minecraft:chests/simple_dungeon", LootContextParamSets.CHEST);
        List<ItemStack> a = t.getRandomItems(chest().create(LootContextParamSets.CHEST), 4242L);
        List<ItemStack> b = t.getRandomItems(chest().create(LootContextParamSets.CHEST), 4242L);
        assertFalse(a.isEmpty());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertTrue(ItemStack.matches(a.get(i), b.get(i)));
    }

    @Test
    void seedZeroStillRolls() {
        LootTable t = LootTable.furnaceOf("minecraft:chests/simple_dungeon", LootContextParamSets.CHEST);
        assertFalse(t.getRandomItems(chest().create(LootContextParamSets.CHEST), 0L).isEmpty());
    }

    @Test
    void dynamicDropCallbackReachesTheServerTable() {
        LootTable t = LootTable.furnaceOf("minecraft:blocks/decorated_pot", LootContextParamSets.BLOCK);
        final var base = net.minecraft.world.level.block.Blocks.DECORATED_POT.defaultBlockState();
        @SuppressWarnings({"rawtypes", "unchecked"})
        net.minecraft.world.level.block.state.BlockState cracked = (net.minecraft.world.level.block.state.BlockState) base.getProperties().stream()
                .filter(pr -> pr.getName().equals("cracked")).findFirst().map(pr -> (Object) base.setValue((net.minecraft.world.level.block.state.properties.Property) pr, Boolean.TRUE)).orElseThrow();
        var pot = cracked;
        var params = chest().withParameter(LootContextParams.BLOCK_STATE, pot)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withDynamicDrop(ResourceLocation.withDefaultNamespace("sherds"), out -> out.accept(new ItemStack(net.minecraft.world.item.Items.BRICK, 3)))
                .create(LootContextParamSets.BLOCK);
        List<ItemStack> drops = t.getRandomItems(params, 1L);
        assertTrue(drops.stream().anyMatch(s -> s.getItem() == net.minecraft.world.item.Items.BRICK && s.getCount() == 3), drops.toString());
    }

    @Test
    void damageSourceAndBlockEntityParametersPassThrough() {
        LootTable t = LootTable.furnaceOf("minecraft:chests/simple_dungeon", LootContextParamSets.CHEST);
        var params = chest().withParameter(LootContextParams.DAMAGE_SOURCE, new FakeDamageSource()).create(WITH_DAMAGE);
        assertFalse(t.getRandomItems(params, 7L).isEmpty());
    }

    @Test
    void damageSourceWithoutTheMethodsIsUnsupportedNotSilent() {
        LootTable t = LootTable.furnaceOf("minecraft:chests/simple_dungeon", LootContextParamSets.CHEST);
        var params = chest().withParameter(LootContextParams.DAMAGE_SOURCE, new Object()).create(WITH_DAMAGE);
        assertThrows(UnsupportedNmsCall.class, () -> t.getRandomItems(params, 7L));
    }
}
