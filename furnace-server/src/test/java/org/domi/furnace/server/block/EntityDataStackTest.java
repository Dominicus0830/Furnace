// M18: 아이템의 entity_data·custom_name 컴포넌트가 놓는 엔티티에 입혀진다(EntityType.createDefaultStackConfig / updateCustomEntityTag / SpawnEggItem.getType).
// 스폰 알(블록에·물에·발사기), 몹 양동이, 배, 수레(놓기·발사기), 갑옷 거치대.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AbstractMinecart;
import org.domi.furnace.server.entity.ArmorStand;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.Cod;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Pig;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityDataStackTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static ItemStack stack(String item, int n) { return new ItemStack(ItemContext.id("minecraft:item", item), n); }

    private static ItemStack withData(ItemStack s, NbtCompound data) { return ItemTools.with(s, "minecraft:entity_data", data); }

    private static ItemStack named(ItemStack s, String name) { return ItemTools.with(s, "minecraft:custom_name", Text.text(name).toJson()); }

    private static <T> List<T> all(BlockPlacementTest.TestLevel level, Class<T> type) {
        return level.entities().tickSnapshot().stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static NbtCompound tag(String id) {
        NbtCompound t = new NbtCompound();
        if (id != null) t.putString("id", id);
        return t;
    }

    @Test
    void spawnEggOnBlockAppliesNameAndEntityData() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        NbtCompound d = tag("minecraft:pig");
        d.putBoolean("NoAI", true);
        d.putBoolean("Silent", true);
        d.putString("CustomName", Text.text("Data").toJson());   // 데이터가 이름보다 나중이라 이긴다
        u.hands[0] = named(withData(stack("pig_spawn_egg", 2), d), "Item");
        Pos pos = new Pos(0, -61, 0);
        var r = BlockPlacement.useItemOn(level, u, 0, new Hit(pos, Dir.UP, 0.5, -60.0, 0.5, false));
        assertTrue(r.consumesAction(), r.toString());
        assertEquals(1, u.hands[0].count());
        Pig pig = all(level, Pig.class).get(0);
        assertTrue(pig.isNoAi());
        assertTrue(pig.isSilent());
        assertEquals("Data", pig.getCustomName().plainText());
    }

    @Test
    void spawnEggTypeComesFromEntityDataId() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        u.hands[0] = withData(stack("pig_spawn_egg", 1), tag("minecraft:cow"));   // 알 종류를 entity_data.id 가 덮는다
        Pos pos = new Pos(0, -61, 0);
        BlockPlacement.useItemOn(level, u, 0, new Hit(pos, Dir.UP, 0.5, -60.0, 0.5, false));
        assertTrue(all(level, Pig.class).isEmpty());
        assertEquals(1, all(level, org.domi.furnace.server.entity.Cow.class).size());
    }

    @Test
    void unknownEntityDataIdFallsBackToEggType() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        u.hands[0] = withData(stack("pig_spawn_egg", 1), tag("minecraft:no_such_thing"));
        Pos pos = new Pos(0, -61, 0);
        BlockPlacement.useItemOn(level, u, 0, new Hit(pos, Dir.UP, 0.5, -60.0, 0.5, false));
        assertEquals(1, all(level, Pig.class).size());
    }

    @Test
    void spawnEggInWaterAppliesEntityData() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:water"), 3);
        var u = new ItemUseTest.User();
        u.y = -59;
        NbtCompound d = tag("minecraft:cow");
        d.putBoolean("NoAI", true);
        u.hands[0] = withData(stack("cow_spawn_egg", 1), d);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertTrue(all(level, org.domi.furnace.server.entity.Cow.class).get(0).isNoAi());
    }

    @Test
    void mobBucketAppliesEntityDataAndName() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        NbtCompound d = tag("minecraft:cod");
        d.putBoolean("Silent", true);
        d.putBoolean("Glowing", true);
        u.hands[0] = named(withData(stack("cod_bucket", 1), d), "Fish");
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        Cod cod = all(level, Cod.class).get(0);
        assertTrue(cod.isSilent());
        assertTrue(cod.hasGlowingTag());
        assertEquals("Fish", cod.getCustomName().plainText());
        assertTrue(cod.fromBucket());
    }

    @Test
    void boatAppliesNameButVariantComesFromItem() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:water"), 3);
        var u = new ItemUseTest.User();
        u.y = -59;
        NbtCompound d = tag("minecraft:boat");
        d.putString("Type", "birch");   // 변종은 아이템이 정한다 — 데이터의 Type 은 이긴다
        d.putBoolean("Invulnerable", true);
        u.hands[0] = named(withData(stack("spruce_boat", 1), d), "Titanic");
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        Boat boat = all(level, Boat.class).get(0);
        assertEquals("Titanic", boat.getCustomName().plainText());
        assertTrue(boat.isInvulnerable());
        assertEquals(Boat.Type.SPRUCE, boat.getVariant());
    }

    @Test
    void minecartOnRailAppliesNameAndData() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:rail"), 3);
        var u = new ItemUseTest.User();
        NbtCompound d = tag("minecraft:minecart");
        d.putBoolean("NoGravity", true);
        u.hands[0] = named(withData(stack("minecart", 1), d), "Cart");
        var r = BlockPlacement.useItemOn(level, u, 0, new Hit(new Pos(0, -60, 0), Dir.UP, 0.5, -59.9, 0.5, false));
        assertTrue(r.consumesAction(), r.toString());
        AbstractMinecart cart = all(level, AbstractMinecart.class).get(0);
        assertEquals("Cart", cart.getCustomName().plainText());
        assertTrue(cart.isNoGravity());
    }

    @Test
    void commandBlockMinecartDataNeedsAnOp() {
        for (int perm : new int[] {0, 2}) {
            var level = new BlockPlacementTest.TestLevel();
            level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:rail"), 3);
            var u = new ItemUseTest.User();
            u.permission = perm;
            NbtCompound d = tag("minecraft:command_block_minecart");
            d.putBoolean("NoGravity", true);
            u.hands[0] = withData(stack("command_block_minecart", 1), d);
            BlockPlacement.useItemOn(level, u, 0, new Hit(new Pos(0, -60, 0), Dir.UP, 0.5, -59.9, 0.5, false));
            AbstractMinecart cart = all(level, AbstractMinecart.class).get(0);
            assertEquals(perm > 0, cart.isNoGravity(), "perm " + perm);
        }
    }

    @Test
    void spawnerMinecartAndFallingBlockDataNeedAnOp() {   // 바닐라 onlyOpCanSetNbt: 명령 블록 수레·스포너 수레·낙하 블록
        for (String type : new String[] {"minecraft:spawner_minecart", "minecraft:falling_block"}) {
            for (int perm : new int[] {0, 2}) {
                var level = new BlockPlacementTest.TestLevel();
                var u = new ItemUseTest.User();
                u.permission = perm;
                NbtCompound d = tag(type);
                d.putBoolean("NoGravity", true);
                u.hands[0] = withData(stack("pig_spawn_egg", 1), d);
                BlockPlacement.useItemOn(level, u, 0, new Hit(new Pos(0, -61, 0), Dir.UP, 0.5, -60.0, 0.5, false));
                var e = level.entities().tickSnapshot().stream().toList();
                assertEquals(1, e.size(), type + " perm " + perm);
                assertEquals(perm > 0, e.get(0).isNoGravity(), type + " perm " + perm);
            }
        }
    }

    @Test
    void entityDataKeepsTheEntityUuid() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        NbtCompound d = tag("minecraft:pig");
        d.putIntArray("UUID", new int[] {1, 2, 3, 4});
        u.hands[0] = withData(stack("pig_spawn_egg", 1), d);
        BlockPlacement.useItemOn(level, u, 0, new Hit(new Pos(0, -61, 0), Dir.UP, 0.5, -60.0, 0.5, false));
        Mob pig = all(level, Pig.class).get(0);
        assertNotEquals(new java.util.UUID(1L << 32 | 2, 3L << 32 | 4), pig.uuid());
    }

    @Test
    void armorStandAppliesNameAndData() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new ItemUseTest.User();
        NbtCompound d = tag("minecraft:armor_stand");
        d.putBoolean("ShowArms", true);
        u.hands[0] = named(withData(stack("armor_stand", 1), d), "Stan");
        var r = BlockPlacement.useItemOn(level, u, 0, new Hit(new Pos(0, -61, 0), Dir.UP, 0.5, -60.0, 0.5, false));
        assertTrue(r.consumesAction(), r.toString());
        ArmorStand stand = all(level, ArmorStand.class).get(0);
        assertEquals("Stan", stand.getCustomName().plainText());
        assertTrue(stand.showArms());
    }

    @Test
    void dispenserSpawnEggAndMinecartKeepName() {
        var level = new DispenserBlockTest.Lvl();
        var be = DispenserBlockTest.place(level, "dispenser", "east");
        be.items[0] = named(stack("pig_spawn_egg", 1), "Dispensed");
        DispenserBlockTest.fire(level);
        assertEquals("Dispensed", all(level, Pig.class).get(0).getCustomName().plainText());
        assertTrue(be.items[0].isEmpty());
    }
}
