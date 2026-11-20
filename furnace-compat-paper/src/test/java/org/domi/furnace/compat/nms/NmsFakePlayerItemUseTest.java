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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
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
 * T-212 다 묶음 2: 월드에 없는 가짜 플레이어의 아이템 쓰기 세 갈래(FAWE {@code PaperweightAdapter#simulateItemUse} 흐름). 블록을 놓으면 Paper 처럼
 * 블록 놓기 이벤트가 나고(플레이어 칸 = 그 가짜의 CraftPlayer), 취소하면 놓지 않는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsFakePlayerItemUseTest {

    static final GameProfile PROFILE = new GameProfile(UUID.nameUUIDFromBytes("worldedit".getBytes()), "[WorldEdit]");
    static final ClientInformation INFO = new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.LEFT, false, false);

    static final class FakePlayer extends ServerPlayer {
        FakePlayer(ServerLevel level) { super(level.getServer(), level, PROFILE, INFO); }
    }

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    BlockPos base;
    FakePlayer fake;
    final List<String> placed = new CopyOnWriteArrayList<>();
    volatile boolean cancel;
    /** 데이터 팩을 안 거는 시험 서버는 아이템 태그 표가 없다(실서버는 DataPackManager 가 건다) — 화살 태그(minecraft:arrows)를 보는 활·쇠뇌 때문에 바닐라 표를 건다. */
    org.domi.furnace.server.net.registry.TagSet tagsBefore;

    @BeforeAll
    void start() {
        tagsBefore = org.domi.furnace.server.item.ItemContext.tagSet();
        if (tagsBefore == null) org.domi.furnace.server.item.ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load(
                "registries/tags-" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt"));
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Builder");
        server.tick(3);
        var loc = tp.player().location();
        base = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        fake = new FakePlayer(level);
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.block.BlockPlaceEvent.class,
                EventPriority.NORMAL, false, e -> {
                    // Bukkit BlockPlaceEvent 의 플레이어 칸이 되는 객체(Compat.player)가 그 가짜의 CraftPlayer 인가
                    placed.add(e.player().name() + " " + e.placedType() + " " + (Compat.player(e.player()) == fake.getBukkitEntity()));
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
        placed.clear();
        cancel = false;
        set(base, Blocks.STONE.defaultBlockState());
        set(base.above(), Blocks.AIR.defaultBlockState());
    }

    void set(BlockPos p, BlockState s) { server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), () -> level.setBlock(p, s, 3)); }

    BlockState get(BlockPos p) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), () -> level.getBlockState(p)); }

    /** FAWE simulateItemUse 의 앞 절반: 손에 쥐고, 그 칸으로 옮겨 누르는 면을 본다. */
    BlockHitResult aim(ItemStack stack, BlockPos pos, Direction face) {
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);
        fake.absMoveTo(pos.getX(), pos.getY(), pos.getZ(), 0.0F, face == Direction.UP ? -90.0F : 0.0F);
        return new BlockHitResult(Vec3.atLowerCornerOf(pos), face, pos, false);
    }

    @Test
    void useOnPlacesTheBlockAndFiresThePlaceEventWithTheFakePlayer() {
        ItemStack stone = new ItemStack(Items.STONE, 2);
        BlockHitResult hit = aim(stone, base, Direction.UP);
        InteractionResult r = stone.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND, hit));
        assertEquals(InteractionResult.CONSUME, r);   // 서버의 BlockItem#place 는 sidedSuccess(false)
        assertSame(Blocks.STONE.defaultBlockState(), get(base.above()));
        assertEquals(1, stone.getCount(), "바닐라처럼 손의 그 스택 객체가 줄어든다");
        assertSame(stone, fake.getItemInHand(InteractionHand.MAIN_HAND));
        assertEquals(List.of("[WorldEdit] minecraft:stone true"), placed);
    }

    @Test
    void cancellingThePlaceEventKeepsTheBlockAndTheItem() {
        cancel = true;
        ItemStack stone = new ItemStack(Items.STONE, 1);
        BlockHitResult hit = aim(stone, base, Direction.UP);
        assertEquals(InteractionResult.FAIL, stone.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND, hit)));
        assertTrue(get(base.above()).isAir());
        assertEquals(1, stone.getCount());
        assertEquals(1, placed.size());
    }

    @Test
    void useItemOnPutsAFlowerInAPot() {
        set(base, Blocks.FLOWER_POT.defaultBlockState());
        ItemStack flower = new ItemStack(Items.DANDELION, 1);
        BlockHitResult hit = aim(flower, base, Direction.UP);
        ItemInteractionResult r = get(base).useItemOn(flower, level, fake, InteractionHand.MAIN_HAND, hit);
        assertTrue(r.consumesAction());
        assertSame(Blocks.POTTED_DANDELION.defaultBlockState(), get(base));
        assertEquals(0, flower.getCount());
        assertTrue(placed.isEmpty(), "화분에 꽂기는 블록 놓기 이벤트가 아니다");
    }

    @Test
    void itemUseFollowsVanillaFoodRules() {
        ItemStack apple = new ItemStack(Items.APPLE, 1);
        aim(apple, base, Direction.UP);
        var r = Items.APPLE.use(level, fake, InteractionHand.MAIN_HAND);
        assertEquals(InteractionResult.FAIL, r.getResult(), "배가 부르면 못 먹는다(Item#use fail)");
        assertSame(apple, r.getObject());

        ItemStack golden = new ItemStack(Items.GOLDEN_APPLE, 1);
        aim(golden, base, Direction.UP);
        assertEquals(InteractionResult.CONSUME, Items.GOLDEN_APPLE.use(level, fake, InteractionHand.MAIN_HAND).getResult());

        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, 1);
        aim(honey, base, Direction.UP);
        assertEquals(InteractionResult.CONSUME, Items.HONEY_BOTTLE.use(level, fake, InteractionHand.MAIN_HAND).getResult(),
                "꿀 병은 배가 불러도 마신다(HoneyBottleItem: startUsingInstantly)");
    }

    /** 바닐라 ItemUtils.createFilledResult: 양동이 하나로 물을 뜨면 손의 그 객체는 빈 스택이 되고, 물 양동이를 돌려준다. 둘이면 하나 줄어든 손 객체. */
    @Test
    void fillingABucketShrinksTheHeldStackLikeVanilla() {
        BlockPos water = base.above();
        set(water, Blocks.WATER.defaultBlockState());
        try {
            ItemStack one = new ItemStack(Items.BUCKET, 1);
            fake.setItemInHand(InteractionHand.MAIN_HAND, one);
            fake.absMoveTo(water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 0.0F, 90.0F);   // 물 바로 위에서 아래를 본다
            var r = Items.BUCKET.use(level, fake, InteractionHand.MAIN_HAND);
            assertEquals(InteractionResult.CONSUME, r.getResult());
            assertSame(Items.WATER_BUCKET, r.getObject().getItem());
            assertTrue(one.isEmpty(), "손의 양동이 객체는 다 써서 빈 스택");

            set(water, Blocks.WATER.defaultBlockState());
            ItemStack two = new ItemStack(Items.BUCKET, 2);
            fake.setItemInHand(InteractionHand.MAIN_HAND, two);
            var r2 = Items.BUCKET.use(level, fake, InteractionHand.MAIN_HAND);
            assertEquals(InteractionResult.CONSUME, r2.getResult());
            assertSame(two, r2.getObject());
            assertEquals(1, two.getCount());
        } finally {
            set(water, Blocks.AIR.defaultBlockState());
        }
    }

    /** FAWE simulateItemUse 그대로: useOn 이 SUCCESS 가 아니면 블록 useItemOn, 그다음 Item#use. 서버 결과는 CONSUME 이라 Paper 처럼 거짓이다. */
    @Test
    void faweSimulateItemUseFlow() {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        BlockHitResult hit = aim(stack, base, Direction.UP);
        InteractionResult result = stack.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND, hit));
        if (result != InteractionResult.SUCCESS) {
            if (get(base).useItemOn(stack, level, fake, InteractionHand.MAIN_HAND, hit).consumesAction()) result = InteractionResult.SUCCESS;
            else result = stack.getItem().use(level, fake, InteractionHand.MAIN_HAND).getResult();
        }
        assertFalse(result == InteractionResult.SUCCESS);
        assertSame(Blocks.STONE.defaultBlockState(), get(base.above()));
    }

    /** 바닐라 BowItem#use: 손(왼손 먼저)·인벤토리에 화살이 있거나 무한 재료면 당기기 시작(consume), 아니면 fail. 시작만으로는 화살을 쓰지 않는다. */
    @Test
    void bowStartsDrawingOnlyWithArrows() {
        ItemStack bow = new ItemStack(Items.BOW, 1);
        aim(bow, base, Direction.UP);
        fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var r = Items.BOW.use(level, fake, InteractionHand.MAIN_HAND);
        assertEquals(InteractionResult.FAIL, r.getResult());
        assertSame(bow, r.getObject());

        ItemStack arrows = new ItemStack(Items.ARROW, 5);
        fake.setItemInHand(InteractionHand.OFF_HAND, arrows);
        try {
            var r2 = Items.BOW.use(level, fake, InteractionHand.MAIN_HAND);
            assertEquals(InteractionResult.CONSUME, r2.getResult());
            assertSame(bow, r2.getObject());
            assertEquals(5, arrows.getCount());
            assertSame(arrows, fake.getItemInHand(InteractionHand.OFF_HAND));
        } finally {
            fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    /** 바닐라 TridentItem#use: 너무 닳았으면(피해 ≥ 최대 − 1) fail, 아니면 쓰기 시작. */
    @Test
    void tridentStartsUnlessTooDamaged() {
        ItemStack trident = new ItemStack(Items.TRIDENT, 1);
        aim(trident, base, Direction.UP);
        assertEquals(InteractionResult.CONSUME, Items.TRIDENT.use(level, fake, InteractionHand.MAIN_HAND).getResult());

        ItemStack worn = ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:trident", 1).withComponent("minecraft:damage", "249"), 1);
        aim(worn, base, Direction.UP);
        var r = Items.TRIDENT.use(level, fake, InteractionHand.MAIN_HAND);
        assertEquals(InteractionResult.FAIL, r.getResult());
        assertSame(worn, r.getObject());
    }

    /**
     * 바닐라 CrossbowItem#use: 빈 쇠뇌는 탄약이 있으면 장전을 시작하고 없으면 fail. 장전된 쇠뇌는 쏘고 consume — 손의 그 객체가 장전이 비고 내구도
     * 1 이 깎이며, 화살이 월드에 들어간다(투사체 발사 이벤트의 쏜 쪽은 그 가짜의 CraftPlayer).
     */
    @Test
    void crossbowChargesOrShoots() {
        ItemStack empty = new ItemStack(Items.CROSSBOW, 1);
        aim(empty, base, Direction.UP);
        fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        assertEquals(InteractionResult.FAIL, Items.CROSSBOW.use(level, fake, InteractionHand.MAIN_HAND).getResult());
        ItemStack arrows = new ItemStack(Items.ARROW, 3);
        fake.setItemInHand(InteractionHand.OFF_HAND, arrows);
        try {
            assertEquals(InteractionResult.CONSUME, Items.CROSSBOW.use(level, fake, InteractionHand.MAIN_HAND).getResult());
            assertEquals(3, arrows.getCount(), "장전 시작만으로는 화살을 쓰지 않는다");
        } finally {
            fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        BlockPos target = base.east(3);
        set(target, Blocks.STONE.defaultBlockState());
        set(target.above(), Blocks.AIR.defaultBlockState());
        List<String> launched = new CopyOnWriteArrayList<>();
        var sub = server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.ProjectileLaunchEvent.class,
                EventPriority.NORMAL, false, e -> launched.add(e.entity().type() + " "
                        + (Compat.entity(e.entity()) instanceof org.bukkit.entity.Projectile pr && pr.getShooter() == fake.getBukkitEntity())));
        try {
            ItemStack charged = ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:crossbow", 1)
                    .withComponent("minecraft:charged_projectiles", "[{id:\"minecraft:arrow\",count:1}]"), 1);
            fake.setItemInHand(InteractionHand.MAIN_HAND, charged);
            fake.absMoveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 0.0F, 90.0F);   // 돌 바로 위에서 아래로 쏜다
            var r = Items.CROSSBOW.use(level, fake, InteractionHand.MAIN_HAND);
            assertEquals(InteractionResult.CONSUME, r.getResult());
            assertSame(charged, r.getObject());
            assertSame(charged, fake.getItemInHand(InteractionHand.MAIN_HAND));
            assertEquals(1, charged.getDamageValue());
            assertEquals(List.of("minecraft:arrow true"), launched);
            server.tick(5);   // 쏜 쪽이 월드에 없는 플레이어인 화살이 돌에 박혀도 탈이 없다
            assertTrue(Items.CROSSBOW.use(level, fake, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "장전이 비었다");
        } finally {
            sub.unsubscribe();
            set(target, Blocks.AIR.defaultBlockState());
        }
    }

    @Test
    void notYetSupportedCallsSayWhy() {
        ItemStack rod = new ItemStack(Items.FISHING_ROD, 1);
        aim(rod, base, Direction.UP);
        assertThrows(UnsupportedNmsCall.class, () -> Items.FISHING_ROD.use(level, fake, InteractionHand.MAIN_HAND));
        ServerPlayer online = ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle();
        assertThrows(UnsupportedNmsCall.class, () -> online.getItemInHand(InteractionHand.MAIN_HAND));
        assertThrows(UnsupportedNmsCall.class, () -> Items.APPLE.use(level, online, InteractionHand.MAIN_HAND));
    }
}
