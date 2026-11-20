package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 3번 묶음 앞부분: {@code ServerPlayer#gameMode} 의 블록 부수기(VeinMiner)·게임 모드(squaremap), Paper {@code Block#getExpDrop}(VeinMiner).
 * 부수기는 손으로 부순 것과 같은 길이다 — 블록 부수기 이벤트(취소하면 안 부숨)·도구 닳기. 다른 region·전역 스레드는 넘기고 true(결정 D3-1).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsPlayerBreakTest {

    static final GameProfile PROFILE = new GameProfile(UUID.nameUUIDFromBytes("breaker".getBytes()), "[Breaker]");
    static final ClientInformation INFO = new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false);

    static final class FakePlayer extends ServerPlayer {
        FakePlayer(ServerLevel level) { super(level.getServer(), level, PROFILE, INFO); }
    }

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    ServerPlayer handle;
    ServerLevel level;
    BlockPos base;
    final List<String> broken = new CopyOnWriteArrayList<>();
    volatile boolean cancel;
    /** 데이터 팩을 안 거는 시험 서버는 태그 표가 없다 — 곡괭이로 캘 블록(mineable/pickaxe)을 보는 맞는 도구 판정 때문에 바닐라 표를 건다. */
    org.domi.furnace.server.net.registry.TagSet tagsBefore;

    @BeforeAll
    void start() {
        tagsBefore = org.domi.furnace.server.item.ItemContext.tagSet();
        if (tagsBefore == null) org.domi.furnace.server.item.ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load(
                "registries/tags-" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt"));
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Miner");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        handle = bukkit.getHandle();
        level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var loc = tp.player().location();
        base = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 2);
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.block.BlockBreakEvent.class,
                EventPriority.NORMAL, false, e -> {
                    broken.add(e.player().name() + " " + e.blockType() + " " + e.pos().y());
                    if (cancel) e.setCancelled(true);
                });
    }

    @AfterAll
    void stop() {
        server.close();
        org.domi.furnace.server.item.ItemContext.installTags(tagsBefore);
    }

    @BeforeEach
    void reset() {
        broken.clear();
        cancel = false;
        on(base, () -> {
            bukkit.setGameMode(GameMode.SURVIVAL);
            return null;
        });
        // 손(고른 칸 0)에 새 곡괭이 — 쓰기가 서버 인벤토리에 닿을 때까지 기다린다(Bukkit setItem 은 플레이어 인벤토리에 비동기로 쓴다)
        assertEquals(0, on(base, () -> bukkit.getInventory().getHeldItemSlot()));
        var put = tp.player().inventory().set(0, org.domi.furnace.api.item.ItemStack.of("minecraft:diamond_pickaxe"));
        for (int i = 0; i < 20 && !put.isDone(); i++) server.tick(1);   // 시험 서버는 손으로 틱을 돌린다
        put.join();
        set(base, Blocks.STONE.defaultBlockState());
    }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    void set(BlockPos p, BlockState s) { on(p, () -> level.setBlock(p, s, 3)); }

    BlockState get(BlockPos p) { return on(p, () -> level.getBlockState(p)); }

    /** 손의 곡괭이가 닳은 값(인벤토리 반영은 연결 쪽에서 끝난다 — 몇 틱 기다린다). */
    int handDamage(int expect) {
        int d = -1;
        for (int i = 0; i < 20; i++) {
            d = on(base, () -> ((Damageable) bukkit.getInventory().getItemInMainHand().getItemMeta()).getDamage());
            if (d == expect) return d;
            server.tick(1);
        }
        return d;
    }

    @Test
    void destroyBlockOnTheOwningRegionIsLikeBreakingByHand() {
        assertTrue(on(base, () -> handle.gameMode.destroyBlock(base)));
        assertTrue(get(base).isAir());
        assertEquals(List.of("Miner minecraft:stone " + base.getY()), broken);
        assertEquals(1, handDamage(1), "곡괭이가 한 번 닳는다(mineBlock)");
    }

    @Test
    void cancellingTheBreakEventKeepsTheBlock() {
        cancel = true;
        assertFalse(on(base, () -> handle.gameMode.destroyBlock(base)));
        assertSame(Blocks.STONE.defaultBlockState(), get(base));
        assertEquals(1, broken.size());
        assertEquals(0, handDamage(0));
    }

    @Test
    void aNonTickThreadWaitsForTheRealResult() {
        cancel = true;
        assertFalse(handle.gameMode.destroyBlock(base), "시험 스레드는 주인에게 부탁해 기다린다");
        cancel = false;
        assertTrue(handle.gameMode.destroyBlock(base));
        assertTrue(get(base).isAir());
    }

    @Test
    void anotherTickThreadHandsOffAndAnswersTrue() {
        cancel = true;
        assertTrue(server.onGlobal(() -> handle.gameMode.destroyBlock(base)), "넘기고 '부쉈다'(결정 D3-1) — 취소돼도 미리 답한다");
        for (int i = 0; i < 20 && broken.isEmpty(); i++) server.tick(1);
        assertEquals(1, broken.size(), "넘긴 쪽에서 이벤트가 났다");
        assertSame(Blocks.STONE.defaultBlockState(), get(base), "취소됐으니 실제로는 안 부숴졌다");
        cancel = false;
        assertTrue(server.onGlobal(() -> handle.gameMode.destroyBlock(base)));
        for (int i = 0; i < 20 && !get(base).isAir(); i++) server.tick(1);
        assertTrue(get(base).isAir());
    }

    @Test
    void unloadedChunkIsRefused() {
        assertThrows(IllegalStateException.class, () -> handle.gameMode.destroyBlock(new BlockPos(90000, 64, 90000)));
    }

    @Test
    void gameModeIsReadFromThePlayer() {
        assertSame(GameType.SURVIVAL, handle.gameMode.getGameModeForPlayer());
        assertTrue(handle.gameMode.isSurvival());
        on(base, () -> {
            bukkit.setGameMode(GameMode.CREATIVE);
            return null;
        });
        assertSame(GameType.CREATIVE, handle.gameMode.getGameModeForPlayer());
        assertTrue(handle.gameMode.isCreative());
        assertFalse(handle.gameMode.isSurvival());
    }

    @Test
    void detachedPlayerBreaksWithItsOwnHands() {
        var fake = new FakePlayer(level);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE, 1);
        fake.setItemInHand(InteractionHand.MAIN_HAND, pick);
        assertTrue(fake.gameMode.destroyBlock(base));
        assertTrue(get(base).isAir());
        assertEquals(List.of("[Breaker] minecraft:stone " + base.getY()), broken, "Paper 처럼 가짜 플레이어도 이벤트를 낸다");
        assertSame(pick, fake.getItemInHand(InteractionHand.MAIN_HAND), "바닐라처럼 손의 그 객체가 닳는다");
        assertEquals(1, pick.getDamageValue());
    }

    @Test
    void expDropMatchesPaper() {
        BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE, 1);
        assertEquals(0, ore.getBlock().getExpDrop(ore, level, base, pick, false), "dropExperience 거짓이면 0");
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(0, stone.getBlock().getExpDrop(stone, level, base, pick, true), "경험치를 안 주는 블록");
        for (int i = 0; i < 20; i++) {
            int xp = ore.getBlock().getExpDrop(ore, level, base, pick, true);
            assertTrue(xp >= 3 && xp <= 7, "다이아몬드 광석 3..7: " + xp);
        }
        var silk = new org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE);
        silk.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
        assertEquals(0, ore.getBlock().getExpDrop(ore, level, base, CraftItemStack.asNMSCopy(silk), true), "섬세한 손길이면 0");
        assertThrows(IllegalStateException.class, () -> ore.getBlock().getExpDrop(ore, level, new BlockPos(90000, 64, 90000), pick, true));
    }
}
