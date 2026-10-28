// 깔때기 아이템 이동: 8틱 주기, 깔때기→깔때기 쿨다운(7/8), 꺼진 깔때기, 화로 면 규칙(위 = 재료, 옆 = 연료, 아래 = 결과·양동이).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BarrelBlockEntity;
import org.domi.furnace.server.block.entity.FurnaceBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HopperTransferTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    private static int hopper(String facing) { return B.prop("facing").with(B.defaultState("hopper"), facing); }

    private static HopperBlockEntity placeHopper(PoiManagerTest.Lvl l, Pos p, String facing) {
        l.setBlock(p, hopper(facing), 3);
        return (HopperBlockEntity) l.getBlockEntity(p);
    }

    @Test
    void ejectsOneItemEveryEightTicks() {
        var l = new PoiManagerTest.Lvl();
        Pos hp = new Pos(0, -50, 0), bp = new Pos(0, -51, 0);
        l.setBlock(bp, B.defaultState("barrel"), 3);
        var barrel = (BarrelBlockEntity) l.getBlockEntity(bp);
        var h = placeHopper(l, hp, "down");
        assertNotNull(h);
        h.items[0] = stack("stone", 3);

        int[] counts = new int[20];
        for (int t = 0; t < 20; t++) {
            h.tick(l);
            counts[t] = barrel.items[0].count();
        }
        assertEquals(1, counts[0], "첫 틱에 하나");
        assertEquals(1, counts[7], "8틱 쿨다운 동안은 그대로");
        assertEquals(2, counts[8], "9번째 틱에 둘째");
        assertEquals(2, counts[15]);
        assertEquals(3, counts[16]);
        assertTrue(h.items[0].isEmpty());
        assertEquals(3, barrel.items[0].count(), "더 없다");
        assertEquals(5, h.cooldownTime, "17번째 틱에 8, 그 뒤 세 틱");
    }

    @Test
    void hopperToHopperCooldownDependsOnTickOrder() {
        // A(동쪽으로) → B(아래는 공기). B 가 먼저 돌았으면 B 쿨다운 7, 나중에 돌면 8(곧 자기 틱에 7).
        for (boolean bFirst : new boolean[] {true, false}) {
            var l = new PoiManagerTest.Lvl();
            var a = placeHopper(l, new Pos(0, -50, 0), "east");
            var b = placeHopper(l, new Pos(1, -50, 0), "down");
            a.items[0] = stack("dirt", 1);
            if (bFirst) b.tick(l);
            else b.tickedGameTime = l.gameTime() - 1;   // 지난 틱에 돈 B
            a.tick(l);
            assertEquals(1, b.items[0].count());
            assertEquals(bFirst ? 7 : 8, b.cooldownTime, bFirst ? "먼저 돈 B" : "아직 안 돈 B");
            if (!bFirst) {
                b.tick(l);
                assertEquals(7, b.cooldownTime);
            }
        }
    }

    @Test
    void disabledHopperDoesNothing() {
        var l = new PoiManagerTest.Lvl();
        Pos hp = new Pos(0, -50, 0), bp = new Pos(0, -51, 0);
        l.setBlock(bp, B.defaultState("barrel"), 3);
        var h = placeHopper(l, hp, "down");
        l.setBlock(hp, B.prop("enabled").with(l.getBlockState(hp), false), 2);
        h.items[0] = stack("stone", 3);
        for (int t = 0; t < 10; t++) h.tick(l);
        assertEquals(3, h.items[0].count());
        assertTrue(((BarrelBlockEntity) l.getBlockEntity(bp)).items[0].isEmpty());
    }

    @Test
    void furnaceFaces() {
        var l = new PoiManagerTest.Lvl();
        // 화로 아래 깔때기: 아래 면은 결과(2) 다음 연료(1, 양동이만)
        Pos fp = new Pos(0, -50, 0);
        l.setBlock(fp, B.defaultState("furnace"), 3);
        var f = (FurnaceBlockEntity) l.getBlockEntity(fp);
        f.items[1] = stack("coal", 4);
        f.items[2] = stack("iron_ingot", 2);
        var below = placeHopper(l, new Pos(0, -51, 0), "down");
        below.tick(l);
        assertEquals(1, below.items[0].count());
        assertEquals("minecraft:iron_ingot", ItemContext.name("minecraft:item", below.items[0].itemId()));
        f.items[2] = ItemStack.EMPTY;
        for (int t = 0; t < 9; t++) below.tick(l);
        assertEquals(4, f.items[1].count(), "연료 칸의 석탄은 아래로 빼지 않는다");

        // 위에서 넣으면 재료 칸(0), 옆에서 넣으면 연료 칸(1)만 — 연료가 아니면 들어가지 않는다
        var l2 = new PoiManagerTest.Lvl();
        Pos fp2 = new Pos(0, -50, 0);
        l2.setBlock(fp2, B.defaultState("furnace"), 3);
        var f2 = (FurnaceBlockEntity) l2.getBlockEntity(fp2);
        var top = placeHopper(l2, new Pos(0, -49, 0), "down");
        top.items[0] = stack("raw_iron", 2);
        top.tick(l2);
        assertEquals(1, f2.items[0].count(), "위 = 재료 칸");
        var side = placeHopper(l2, new Pos(-1, -50, 0), "east");
        side.items[0] = stack("raw_iron", 1);
        side.items[1] = stack("coal", 1);
        side.tick(l2);
        assertEquals(1, f2.items[1].count(), "옆 = 연료 칸: 원석은 건너뛰고 석탄");
        assertEquals(1, side.items[0].count());
    }

    @Test
    void hopperMinecartSucksFromContainerAbove() {
        var l = new PoiManagerTest.Lvl();
        Pos bp = new Pos(0, -50, 0);
        l.setBlock(bp, B.defaultState("barrel"), 3);
        var barrel = (BarrelBlockEntity) l.getBlockEntity(bp);
        barrel.items[3] = stack("stone", 2);
        var cart = new org.domi.furnace.server.entity.MinecartHopper(l, 77);
        cart.setPos(0.5, -51.0, 0.5);   // 기준 y = -50.5, 위 칸 = -50
        assertTrue(cart.suckInItems());
        assertEquals(1, cart.getItem(0).count());
        assertEquals(1, barrel.items[3].count());
        cart.setEnabled(false);
        assertTrue(cart.suckInItems(), "suckInItems 자체는 켜짐과 무관(틱이 막는다)");
    }

    @Test
    void hopperBelowJukeboxIsLockedWhileSongPlaysThenTakesDisc() {
        // 바닐라: 재생 시작·정지 때 notifyChange(updateNeighborsAt) — 재생 중 주크박스는 신호 15 라 아래 깔때기가 잠겨(enabled=false) 음반을 못 뺀다.
        // 노래가 끝나 정지(1011)하면 깔때기가 풀리고 다음 틱에 음반을 가져간다(이미 멈춘 뒤라 1011 은 다시 나가지 않는다).
        org.domi.furnace.server.item.JukeboxSongs.put("minecraft:13", 178.0F, 1);
        var events = new java.util.ArrayList<int[]>();
        var l = new PoiManagerTest.Lvl() {
            @Override public void levelEvent(org.domi.furnace.server.block.BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, data}); }
        };
        Pos jp = new Pos(0, -49, 0), hp = new Pos(0, -50, 0);
        l.setBlock(jp, B.defaultState("minecraft:jukebox"), 3);
        var jb = (org.domi.furnace.server.block.entity.JukeboxBlockEntity) l.getBlockEntity(jp);
        assertNotNull(jb);
        var h = placeHopper(l, hp, "down");
        ItemStack disc = stack("music_disc_13", 1);
        org.junit.jupiter.api.Assumptions.assumeTrue(org.domi.furnace.server.item.JukeboxSongs.of(disc) != null, "아이템 기본 컴포넌트 없음");
        jb.insert(disc, null, l);
        assertTrue(jb.isPlaying());
        assertFalse(B.prop("enabled").is(l.getBlockState(hp)), "재생 중 신호 15 로 깔때기가 잠긴다");
        h.tick(l);
        assertFalse(jb.theItem().isEmpty(), "잠긴 깔때기는 음반을 못 뺀다");
        int total = 178 * 20 + 20;
        for (int i = 0; i <= total; i++) jb.tick(l);
        assertFalse(jb.isPlaying());
        assertEquals(1011, events.get(events.size() - 1)[0], "노래가 끝나 정지 이벤트");
        assertTrue(B.prop("enabled").is(l.getBlockState(hp)), "정지 뒤 깔때기가 풀린다");
        int n = events.size();
        h.tick(l);
        assertTrue(jb.theItem().isEmpty(), "풀린 깔때기가 음반을 가져간다");
        assertFalse(B.prop("has_record").is(l.getBlockState(jp)));
        assertEquals(n, events.size(), "이미 멈춘 노래라 1011 이 다시 나가지 않는다");
        assertFalse(h.items[0].isEmpty());
    }

    @Test
    void jukeboxIsVanillaSingleItemContainer() {
        // 바닐라 JukeboxBlockEntity(ContainerSingleItem): 칸 1, 최대 1, 음반만·빈 칸에만, 빼면 통째로 + 정지(1011), 받는 쪽 빈 칸 필요
        org.domi.furnace.server.item.JukeboxSongs.put("minecraft:13", 178.0F, 1);
        var events = new java.util.ArrayList<int[]>();
        var l = new PoiManagerTest.Lvl() {
            @Override public void levelEvent(org.domi.furnace.server.block.BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, data}); }
        };
        Pos jp = new Pos(2, -49, 2);
        l.setBlock(jp, B.defaultState("minecraft:jukebox"), 3);
        var jb = (org.domi.furnace.server.block.entity.JukeboxBlockEntity) l.getBlockEntity(jp);
        ItemStack disc = stack("music_disc_13", 1);
        org.junit.jupiter.api.Assumptions.assumeTrue(org.domi.furnace.server.item.JukeboxSongs.of(disc) != null, "아이템 기본 컴포넌트 없음");
        var c = HopperBlockEntity.containerOf(l, jb);
        assertNotNull(c, "/item·execute if items block 이 쓰는 containerOf 에 주크박스가 잡힌다");
        assertSame(jb, c.blockEntity());
        assertEquals(1, c.size());
        assertEquals(1, c.maxStackSize());
        assertEquals(1, c.maxStackSize(stack("stone", 64)));
        assertFalse(c.canPlaceItem(0, stack("stone", 1)), "음반이 아니면 못 넣는다");
        assertTrue(c.canPlaceItem(0, disc));
        c.setItem(0, disc);
        assertTrue(jb.isPlaying());
        assertEquals(1010, events.get(events.size() - 1)[0]);
        assertTrue(B.prop("has_record").is(l.getBlockState(jp)));
        assertFalse(c.canPlaceItem(0, disc), "찬 칸엔 못 넣는다");
        var full = HopperBlockEntity.containerOf(l, placeHopper(l, new Pos(5, -50, 5), "down"));
        for (int i = 0; i < full.size(); i++) ((HopperBlockEntity) full.blockEntity()).items[i] = stack("stone", 64);
        assertFalse(c.canTakeItem(full, 0, disc), "받는 쪽에 빈 칸이 없으면 못 뺀다");
        ((HopperBlockEntity) full.blockEntity()).items[4] = stack("stone", 1);
        assertFalse(c.canTakeItem(full, 0, disc), "같은 아이템 합치기는 보지 않는다 — 빈 칸만");
        ((HopperBlockEntity) full.blockEntity()).items[4] = ItemStack.EMPTY;
        assertTrue(c.canTakeItem(full, 0, disc));
        ItemStack out = c.removeItem(0, 1);
        assertEquals(disc.itemId(), out.itemId());
        assertFalse(jb.isPlaying());
        assertEquals(1011, events.get(events.size() - 1)[0]);
        assertFalse(B.prop("has_record").is(l.getBlockState(jp)));
        assertTrue(c.get(0).isEmpty());
        assertEquals(0, jb.updateTag().tags().size(), "바닐라는 주크박스 update tag 를 덮어쓰지 않는다(빈 태그)");
    }

    @Test
    void hopperAboveJukeboxInsertsDiscAndStartsSong() {
        org.domi.furnace.server.item.JukeboxSongs.put("minecraft:13", 178.0F, 1);
        var events = new java.util.ArrayList<int[]>();
        var l = new PoiManagerTest.Lvl() {
            @Override public void levelEvent(org.domi.furnace.server.block.BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, data}); }
        };
        Pos jp = new Pos(0, -50, 0), hp = new Pos(0, -49, 0);
        l.setBlock(jp, B.defaultState("minecraft:jukebox"), 3);
        var jb = (org.domi.furnace.server.block.entity.JukeboxBlockEntity) l.getBlockEntity(jp);
        var h = placeHopper(l, hp, "down");
        ItemStack disc = stack("music_disc_13", 1);
        org.junit.jupiter.api.Assumptions.assumeTrue(org.domi.furnace.server.item.JukeboxSongs.of(disc) != null, "아이템 기본 컴포넌트 없음");
        h.items[0] = stack("stone", 1);
        h.items[1] = disc;
        h.tick(l);
        assertEquals(1, h.items[0].count(), "돌은 주크박스에 못 들어가 그대로");
        assertTrue(h.items[1].isEmpty(), "음반은 들어간다");
        assertTrue(jb.isPlaying());
        assertEquals(1010, events.get(events.size() - 1)[0]);
    }
}
