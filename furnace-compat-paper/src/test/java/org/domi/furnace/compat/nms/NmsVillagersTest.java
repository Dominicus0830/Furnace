package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 주민 종류·직업·데이터(엔티티 데이터 설계 D4): 레지스트리 번호 순서·기본값, 직업 성분(직업 블록·요청 아이템·보조 지점·일 소리)과 바이옴 대응이
 * 서버 주민 정의와 같고, 주민 데이터 칸이 월드 밖 엔티티의 getEntityData 와 패킷 다리를 지난다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsVillagersTest {

    FurnaceTestServer server;
    ServerLevel level;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        var tp = server.join("Villagers");
        server.tick(2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    static String key(ResourceLocation l) { return l.getPath(); }

    @Test
    void registriesFollowTheServerOrderAndDefaults() {
        List<String> types = new ArrayList<>();
        for (VillagerType t : BuiltInRegistries.VILLAGER_TYPE) types.add(t.toString());
        assertEquals(Villager.TYPES, types);
        List<String> profs = new ArrayList<>();
        for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) profs.add(p.toString());
        assertEquals(Villager.PROFESSIONS, profs);
        assertSame(VillagerType.PLAINS, BuiltInRegistries.VILLAGER_TYPE.get(ResourceLocation.parse("minecraft:nope")), "기본값 plains");
        assertSame(VillagerProfession.NONE, BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse("minecraft:nope")), "기본값 none");
        assertEquals(2, BuiltInRegistries.VILLAGER_TYPE.getId(VillagerType.PLAINS));
        assertEquals("librarian", VillagerProfession.LIBRARIAN.name());
        assertSame(VillagerProfession.FARMER, BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace("farmer")));
    }

    @Test
    void professionsMatchTheServerVillager() {
        var pois = BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
            String n = p.name();
            List<String> req = new ArrayList<>();
            p.requestedItems().forEach(i -> req.add(BuiltInRegistries.ITEM.getKey(i).toString()));
            assertEquals(Villager.requestedItems(n), java.util.Set.copyOf(req), n + " 요청 아이템");
            List<String> sec = new ArrayList<>();
            p.secondaryPoi().forEach(b -> sec.add(BuiltInRegistries.BLOCK.getKey(b).toString()));
            assertEquals(Villager.secondaryPoi(n), java.util.Set.copyOf(sec), n + " 보조 지점");
            if (n.equals("none") || n.equals("nitwit")) assertNull(p.workSound(), n);
            else assertEquals("entity.villager.work_" + n, BuiltInRegistries.SOUND_EVENT.getKey(p.workSound()).getPath(), n + " 일 소리");
            for (Holder.Reference<net.minecraft.world.entity.ai.village.poi.PoiType> h : pois.holders().toList()) {
                var sp = org.domi.furnace.server.world.poi.PoiTypes.byName(h.key().location().toString());
                assertNotNull(sp, h.key().toString());
                assertEquals(Villager.heldJobSite(n).test(sp), p.heldJobSite().test(h), n + " 들고 있는 직업 블록 " + h.key().location());
                assertEquals(Villager.acquirableJobSite(n).test(sp), p.acquirableJobSite().test(h), n + " 얻을 직업 블록 " + h.key().location());
            }
        }
        List<String> farmer = new ArrayList<>();
        VillagerProfession.FARMER.requestedItems().forEach(i -> farmer.add(BuiltInRegistries.ITEM.getKey(i).getPath()));
        assertEquals(List.of("wheat", "wheat_seeds", "beetroot_seeds", "bone_meal"), farmer, "바닐라 ImmutableSet 차례");
    }

    @Test
    void biomesMapLikeTheServerVillager() {
        var biomes = org.domi.furnace.api.Furnace.server().gameRegistries().entries("minecraft:worldgen/biome");
        assertEquals(64, biomes.size());
        for (String b : biomes) assertEquals(Villager.typeForBiome(b), NmsVillagers.typeForBiome(b).toString(), b);
        assertSame(VillagerType.PLAINS, NmsVillagers.typeForBiome("other:snowy_plains"), "다른 이름공간은 표에 없다");
        var snowy = ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.withDefaultNamespace("snowy_plains"));
        assertSame(VillagerType.SNOW, VillagerType.byBiome(new Holder.Reference<>(snowy, null)));
        assertSame(VillagerType.PLAINS, VillagerType.byBiome(Holder.direct(null)), "키 없는 손잡이");
    }

    @Test
    void villagerDataIsAValueAndCrossesTheBridge() {
        var d = new VillagerData(VillagerType.TAIGA, VillagerProfession.CLERIC, 0);
        assertEquals(1, d.getLevel(), "1 아래는 1");
        assertEquals(3, d.setLevel(3).getLevel());
        assertSame(VillagerType.TAIGA, d.setProfession(VillagerProfession.MASON).getType());
        assertEquals(10, VillagerData.getMinXpPerLevel(2));
        assertEquals(70, VillagerData.getMaxXpPerLevel(2));
        assertEquals(0, VillagerData.getMaxXpPerLevel(5));
        var api = (org.domi.furnace.api.entity.data.VillagerData) EntityDataBridge.toApi("villager_data", d.setLevel(4), "test");
        assertEquals(new org.domi.furnace.api.entity.data.VillagerData("minecraft:taiga", "minecraft:cleric", 4), api);
        var back = (VillagerData) EntityDataBridge.toShim("villager_data", api, "test");
        assertSame(VillagerType.TAIGA, back.getType());
        assertSame(VillagerProfession.CLERIC, back.getProfession());
        var custom = new VillagerProfession("custom", h -> false, h -> false, com.google.common.collect.ImmutableSet.of(), com.google.common.collect.ImmutableSet.of(), null);
        assertEquals("minecraft:none", ((org.domi.furnace.api.entity.data.VillagerData) EntityDataBridge.toApi("villager_data",
                d.setProfession(custom), "test")).profession(), "등록되지 않은 직업은 바닐라처럼 기본값");

        // 월드 밖 주민의 칸: 바닐라 생성자처럼 plains·무직·1
        var v = EntityType.VILLAGER.create(level);
        var slot = new EntityDataAccessor<VillagerData>(Villager.DATA_VILLAGER_DATA, EntityDataSerializers.VILLAGER_DATA);
        VillagerData now = v.getEntityData().get(slot);
        assertSame(VillagerType.PLAINS, now.getType());
        assertSame(VillagerProfession.NONE, now.getProfession());
        v.getEntityData().set(slot, now.setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
        assertSame(VillagerProfession.LIBRARIAN, v.getEntityData().get(slot).getProfession(), "칸 쓰기가 서버 재생을 지나 돌아온다");
        assertEquals(Optional.of(2), Optional.of(v.getEntityData().get(slot).getLevel()));
    }
}
