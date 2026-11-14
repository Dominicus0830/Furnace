package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * T-141: Material 블록 성질과 EntityType.isAlive/isSpawnable 이 Paper 1.21.1 과 같은 답을 낸다.
 * Paper 는 Material → BlockType → CraftBlockType 로 바닐라 블록 기본 상태를 읽는다(isSolid=blocksMotion, isFlammable=ignitedByLava,
 * isBurnable=불붙음 확률>0, isOccluding=isRedstoneConductor, hasGravity=Fallable). 기대값은 바닐라 블록 정의에서 손으로 정했다.
 */
class MaterialBlockPropertiesTest {

    @Test
    void solidFollowsBlocksMotion() {
        assertTrue(Material.STONE.isSolid());
        assertTrue(Material.GLASS.isSolid());
        assertTrue(Material.OAK_FENCE.isSolid());
        assertTrue(Material.OAK_SIGN.isSolid(), "표지판은 forceSolidOn 이라 blocksMotion 참");
        assertFalse(Material.COBWEB.isSolid(), "거미줄은 blocksMotion 에서 따로 빠진다");
        assertFalse(Material.BAMBOO_SAPLING.isSolid());
        assertFalse(Material.WATER.isSolid());
        assertFalse(Material.AIR.isSolid());
        assertFalse(Material.TORCH.isSolid());
        assertFalse(Material.SHORT_GRASS.isSolid());
        assertFalse(Material.DIAMOND.isSolid(), "블록이 아니면 false");
    }

    @Test
    void occludingFollowsRedstoneConductor() {
        assertTrue(Material.STONE.isOccluding());
        assertTrue(Material.OAK_LOG.isOccluding());
        assertFalse(Material.GLASS.isOccluding());
        assertFalse(Material.OAK_LEAVES.isOccluding());
        assertFalse(Material.ICE.isOccluding());
        assertFalse(Material.OAK_SLAB.isOccluding(), "아래 반 블록 기본 상태는 꽉 차지 않는다");
        assertFalse(Material.DIAMOND.isOccluding());
    }

    @Test
    void flammableAndBurnable() {
        assertTrue(Material.OAK_PLANKS.isBurnable());
        assertTrue(Material.OAK_PLANKS.isFlammable());
        assertTrue(Material.WHITE_WOOL.isBurnable());
        assertTrue(Material.TNT.isBurnable());
        assertTrue(Material.TNT.isFlammable());
        assertFalse(Material.STONE.isBurnable());
        assertFalse(Material.STONE.isFlammable());
        assertFalse(Material.CRIMSON_PLANKS.isBurnable(), "진홍 나무는 불에 타지 않는다");
        assertFalse(Material.CRIMSON_PLANKS.isFlammable());
        assertFalse(Material.STICK.isBurnable(), "블록이 아니면 false");
    }

    @Test
    void gravityIsFallableBlocks() {
        for (Material m : new Material[] {Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.WHITE_CONCRETE_POWDER, Material.ANVIL,
                Material.CHIPPED_ANVIL, Material.DRAGON_EGG, Material.POINTED_DRIPSTONE, Material.SUSPICIOUS_SAND, Material.SUSPICIOUS_GRAVEL})
            assertTrue(m.hasGravity(), m.name());
        assertFalse(Material.SCAFFOLDING.hasGravity(), "비계는 떨어지지만 Fallable 이 아니다");
        assertFalse(Material.STONE.hasGravity());
        assertFalse(Material.WHITE_CONCRETE.hasGravity());
        long n = Arrays.stream(Material.values()).filter(Material::hasGravity).count();
        assertEquals(26, n, "모래 2·자갈·콘크리트 가루 16·모루 3·용 알·점적석·수상한 블록 2");
    }

    @Test
    void transparentIsPaperLegacyList() {
        assertTrue(Material.TORCH.isTransparent());
        assertTrue(Material.AIR.isTransparent());
        assertTrue(Material.OAK_SAPLING.isTransparent());
        assertTrue(Material.REDSTONE_WIRE.isTransparent());
        assertFalse(Material.GLASS.isTransparent(), "옛 목록이라 유리는 빠져 있다(Paper 와 같다)");
        assertFalse(Material.WATER.isTransparent());
        assertFalse(Material.STONE.isTransparent());
        assertFalse(Material.STICK.isTransparent());
        assertEquals(127, Arrays.stream(Material.values()).filter(Material::isTransparent).count());
    }

    @Test
    void collision() {
        assertTrue(Material.STONE.hasCollision());
        assertTrue(Material.OAK_DOOR.hasCollision());
        assertFalse(Material.TORCH.hasCollision());
        assertFalse(Material.AIR.hasCollision());
        assertFalse(Material.OAK_SIGN.hasCollision());
        assertFalse(Material.STICK.hasCollision());
    }

    @Test
    void numericProperties() {
        assertEquals(1.5F, Material.STONE.getHardness());
        assertEquals(6.0F, Material.STONE.getBlastResistance());
        assertEquals(0.6F, Material.STONE.getSlipperiness());
        assertEquals(-1.0F, Material.BEDROCK.getHardness());
        assertEquals(3_600_000.0F, Material.BEDROCK.getBlastResistance());
        assertEquals(0.98F, Material.ICE.getSlipperiness());
        assertEquals(0.989F, Material.BLUE_ICE.getSlipperiness());
        assertEquals(0.8F, Material.SLIME_BLOCK.getSlipperiness());
        assertEquals(50.0F, Material.OBSIDIAN.getHardness());
        assertEquals(1200.0F, Material.OBSIDIAN.getBlastResistance());
        assertEquals(0.0F, Material.TORCH.getHardness());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, Material.DIAMOND::getHardness);
        assertEquals("The Material is not a block!", e.getMessage());
        assertThrows(IllegalArgumentException.class, Material.DIAMOND::getBlastResistance);
        assertThrows(IllegalArgumentException.class, Material.DIAMOND::getSlipperiness);
    }

    @Test
    void entityTypeAliveAndSpawnable() {
        for (EntityType t : new EntityType[] {EntityType.ZOMBIE, EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ENDER_DRAGON,
                EntityType.BAT, EntityType.COD, EntityType.VILLAGER, EntityType.BREEZE, EntityType.GIANT})
            assertTrue(t.isAlive(), t.name());
        for (EntityType t : new EntityType[] {EntityType.ITEM, EntityType.ARROW, EntityType.BOAT, EntityType.MINECART, EntityType.ITEM_FRAME,
                EntityType.PAINTING, EntityType.TNT, EntityType.FALLING_BLOCK, EntityType.EXPERIENCE_ORB, EntityType.END_CRYSTAL,
                EntityType.LIGHTNING_BOLT, EntityType.MARKER, EntityType.BLOCK_DISPLAY, EntityType.UNKNOWN})
            assertFalse(t.isAlive(), t.name());
        assertFalse(EntityType.PLAYER.isSpawnable());
        assertFalse(EntityType.FISHING_BOBBER.isSpawnable());
        assertFalse(EntityType.UNKNOWN.isSpawnable());
        assertTrue(EntityType.ZOMBIE.isSpawnable());
        assertTrue(EntityType.LIGHTNING_BOLT.isSpawnable());
        assertTrue(EntityType.ITEM.isSpawnable());
        assertEquals(EntityType.values().length - 3, Arrays.stream(EntityType.values()).filter(EntityType::isSpawnable).count());
    }
}
