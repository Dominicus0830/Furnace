package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 칸 쓰기 훅(바닐라 SynchedEntityData.set → onSyncedDataUpdated): 값이 바뀔 때만 불리고, 칸에 바로 쓴 값(플러그인 길)도
 * 종류별 setter 와 같은 반응(크기·상자·이름)을 낸다.
 */
class SyncedDataHookTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    @Test
    void hookRunsOnlyWhenTheValueChanges() {
        List<Integer> calls = new ArrayList<>();
        SynchedData d = new SynchedData(calls::add);
        d.define(0, SetEntityData.SERIALIZER_INT, 1);
        d.define(1, SetEntityData.SERIALIZER_BOOLEAN, false);
        assertEquals(List.of(), calls, "define 은 훅을 부르지 않는다");
        d.set(0, 1);
        assertEquals(List.of(), calls, "같은 값");
        d.set(0, 2);
        d.set(1, true);
        assertEquals(List.of(0, 1), calls);
        d.set(1, true, true);
        assertEquals(List.of(0, 1, 1), calls, "force 는 같은 값도 부른다");
        d.setQuiet(0, 5);
        assertEquals(3, calls.size(), "setQuiet 은 부르지 않는다");
    }

    @Test
    void directSlotWritesResizeLikeTheSetters() {
        var level = new BlockPlacementTest.TestLevel();

        var interaction = (Interaction) Entities.create(level, "minecraft:interaction");
        interaction.entityData().set(Interaction.DATA_WIDTH, 2.5F);
        interaction.entityData().set(Interaction.DATA_HEIGHT, 0.75F);
        assertEquals(2.5F, interaction.bbWidth());
        assertEquals(0.75F, interaction.bbHeight());

        var zombie = (Zombie) Entities.create(level, "minecraft:zombie");
        zombie.entityData().set(Zombie.DATA_BABY, true);
        assertEquals(0.3F, zombie.bbWidth());
        zombie.entityData().set(Zombie.DATA_BABY, false);
        assertEquals(0.6F, zombie.bbWidth());

        var pig = (AgeableMob) Entities.create(level, "minecraft:pig");
        pig.entityData().set(AgeableMob.DATA_BABY_ID, true);
        // 바닐라 서버도 크기는 나이 필드(getAge)로 잰다: 칸만 바꾸면 훅은 불려도 어른 크기 그대로
        assertEquals(EntityType.PIG.width(), pig.bbWidth());
        var piglet = (AgeableMob) Entities.create(level, "minecraft:pig");
        piglet.setAge(-24000);   // 나이 필드 → 칸 → 훅 → 크기
        assertEquals(EntityType.PIG.width() * 0.5F, piglet.bbWidth());

        var cloud = (AreaEffectCloud) Entities.create(level, "minecraft:area_effect_cloud");
        cloud.entityData().set(AreaEffectCloud.DATA_RADIUS, 1.5F);
        assertEquals(3.0F, cloud.bbWidth());

        var stand = (ArmorStand) Entities.create(level, "minecraft:armor_stand");
        stand.entityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) 0x01);   // 작게
        assertEquals(0.5F, stand.bbWidth());
    }

    @Test
    void slimeSizeSlotFacesTheHeadAndResizes() {
        var level = new BlockPlacementTest.TestLevel();
        var slime = (Slime) Entities.create(level, "minecraft:slime");
        slime.setYHeadRot(37.0F);
        slime.setYRot(-120.0F);
        slime.entityData().set(Slime.DATA_SIZE, 4);
        assertEquals(0.52F * 4, slime.bbWidth());
        assertEquals(37.0F, slime.yRot(), "바닐라 Slime 훅: setYRot(yHeadRot)");
        assertEquals(37.0F, slime.yBodyRot());
    }

    @Test
    void paintingVariantSlotFollowsTheName() {
        var level = new BlockPlacementTest.TestLevel();
        var painting = (Painting) Entities.create(level, "minecraft:painting");
        int aztec = RegistrySet.current().indexOf("minecraft:painting_variant", "minecraft:aztec");
        painting.entityData().set(Painting.DATA_VARIANT, aztec);
        assertEquals("minecraft:aztec", painting.variant());
    }

    @Test
    void commandMinecartSlotsReachTheCommandLogic() {
        var level = new BlockPlacementTest.TestLevel();
        var cart = (MinecartCommandBlock) Entities.create(level, "minecraft:command_block_minecart");
        cart.entityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, "say hi");
        assertEquals("say hi", cart.command());
        assertSame(cart.entityData().get(MinecartCommandBlock.DATA_ID_COMMAND_NAME), cart.command());
    }

    /** 저장 읽기: 머리 방향은 회전 읽기 직후(readAdditionalSaveData 전)에 맞춰져, 크기 칸 훅의 setYRot(yHeadRot) 이 저장된 방향을 지키는가(PR 570 검토 재현). */
    @Test
    void loadedBigSlimeKeepsItsYaw() {
        var level = new BlockPlacementTest.TestLevel();
        var slime = (Slime) Entities.create(level, "minecraft:slime");
        slime.setSize(4, true);
        slime.moveTo(0.5, 64, 0.5, 123.0F, 0.0F);
        var tag = slime.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        var back = (Slime) Entities.create(level, "minecraft:slime");
        back.load(tag);
        assertEquals(4, back.getSize());
        assertEquals(123.0F, back.yRot(), "저장된 방향");
        assertEquals(123.0F, back.yHeadRot());
    }

    /**
     * 크기 0 팬텀의 공격력 기본값은 몬스터 기본값 2 다: 바닐라 1.21.1 서버 jar 에서 updatePhantomSizeInfo 를 부르는 곳은 훅 하나뿐이라
     * 처음 값과 같은 setPhantomSize(0) 은 6 + 크기를 넣지 않는다(이전 서버는 늘 6 을 넣었다). 크기가 바뀌면 6 + 크기.
     */
    @Test
    void sizeZeroPhantomKeepsTheMonsterAttackBase() {
        var level = new BlockPlacementTest.TestLevel();
        var p = (Phantom) Entities.create(level, "minecraft:phantom");
        var attack = p.attributes().get(org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE);
        p.setPhantomSize(0);
        assertEquals(2.0, attack.baseValue());
        p.setPhantomSize(3);
        assertEquals(9.0, attack.baseValue());
        assertEquals(EntityType.PHANTOM.width() * 1.45F, p.bbWidth(), 1e-6);
    }

    /** Warden: 자세 칸에 바로 써도(플러그인 길) 파고들기 높이 1 이 된다 — Entity 자세 훅 → refreshDimensions. */
    @Test
    void wardenPoseSlotResizes() {
        var level = new BlockPlacementTest.TestLevel();
        var w = (Warden) Entities.create(level, "minecraft:warden");
        w.entityData().set(Entity.DATA_POSE, Entity.POSE_DIGGING);
        assertEquals(1.0F, w.bbHeight());
        w.entityData().set(Entity.DATA_POSE, Entity.POSE_STANDING);
        assertEquals(EntityType.WARDEN.height(), w.bbHeight());
    }
}
