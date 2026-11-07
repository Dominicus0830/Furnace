// M22: 표시 엔티티(transformation 행렬·axis_angle, 글자 표시), Interaction 기록, 효과 구름(Particle·potion_contents·면역), 거치대,
// 엔더맨 투사체 텔레포트, TNT 수레 불화살, 박힌 화살 수.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityNbtDisplayTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList(NbtIo.TAG_FLOAT);
        for (float f : v) l.add(f);
        return l;
    }

    private static NbtCompound display(String id) {
        NbtCompound t = new NbtCompound();
        t.putString("id", id);
        t.put("Pos", doubles(0.5, -60.0, 0.5));
        return t;
    }

    private static NbtList doubles(double... v) {
        NbtList l = new NbtList(NbtIo.TAG_DOUBLE);
        for (double d : v) l.add(d);
        return l;
    }

    private static float[] get(Entity e, int index) { return (float[]) e.entityData.get(index); }

    /** 이동 × 왼쪽 회전 × 크기 × 오른쪽 회전을 행 우선 4×4 로 합친다(바닐라 Transformation.compose). */
    private static float[] compose(float[] t, float[] l, float[] s, float[] r) {
        double[][] m = identity();
        m = mul(m, translation(t));
        m = mul(m, rotation(l));
        m = mul(m, scale(s));
        m = mul(m, rotation(r));
        float[] out = new float[16];
        for (int i = 0; i < 16; i++) out[i] = (float) m[i >> 2][i & 3];
        return out;
    }

    private static double[][] identity() {
        double[][] m = new double[4][4];
        for (int i = 0; i < 4; i++) m[i][i] = 1;
        return m;
    }

    private static double[][] translation(float[] t) {
        double[][] m = identity();
        m[0][3] = t[0];
        m[1][3] = t[1];
        m[2][3] = t[2];
        return m;
    }

    private static double[][] scale(float[] s) {
        double[][] m = identity();
        m[0][0] = s[0];
        m[1][1] = s[1];
        m[2][2] = s[2];
        return m;
    }

    private static double[][] rotation(float[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double[][] m = identity();
        m[0][0] = 1 - 2 * (y * y + z * z);
        m[0][1] = 2 * (x * y - z * w);
        m[0][2] = 2 * (x * z + y * w);
        m[1][0] = 2 * (x * y + z * w);
        m[1][1] = 1 - 2 * (x * x + z * z);
        m[1][2] = 2 * (y * z - x * w);
        m[2][0] = 2 * (x * z - y * w);
        m[2][1] = 2 * (y * z + x * w);
        m[2][2] = 1 - 2 * (x * x + y * y);
        return m;
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    // ------------------------------------------------------------------ 표시 엔티티

    @Test
    void matrixTransformationIsDecomposedIntoTranslationAndScale() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:block_display");
        in.putList("transformation", floats(2, 0, 0, 3, 0, 2, 0, 4, 0, 0, 2, 5, 0, 0, 0, 1));
        Entity e = Entities.load(level, in);
        assertArrayEquals(new float[] {3, 4, 5}, get(e, Display.DATA_TRANSLATION), 1e-6F);
        assertArrayEquals(new float[] {2, 2, 2}, get(e, Display.DATA_SCALE), 1e-5F);
        // 회전이 없으면 단위 회전(부호만 다른 쿼터니언도 허용)
        for (int idx : new int[] {Display.DATA_LEFT_ROT, Display.DATA_RIGHT_ROT}) {
            float[] q = get(e, idx);
            assertEquals(0, q[0], 1e-5F);
            assertEquals(0, q[1], 1e-5F);
            assertEquals(0, q[2], 1e-5F);
            assertEquals(1, Math.abs(q[3]), 1e-5F);
        }
        // 저장은 바닐라 복합 태그 모양
        NbtCompound t = (NbtCompound) e.save().tags().get("transformation");
        assertTrue(t.tags().get("translation") instanceof NbtList);
        assertTrue(t.tags().get("left_rotation") instanceof NbtList l && l.size() == 4);
    }

    @Test
    void rotatedAndScaledMatrixRecomposesToTheSameMatrix() {
        var level = new BlockPlacementTest.TestLevel();
        // 이동(1,2,3) × 회전(y 로 90°) × 크기(1,2,3)
        float s = (float) Math.sqrt(0.5);
        float[] m = compose(new float[] {1, 2, 3}, new float[] {0, s, 0, s}, new float[] {1, 2, 3}, new float[] {0, 0, 0, 1});
        NbtCompound in = display("minecraft:item_display");
        in.putList("transformation", floats(m));
        Entity e = Entities.load(level, in);
        float[] back = compose(get(e, Display.DATA_TRANSLATION), get(e, Display.DATA_LEFT_ROT), get(e, Display.DATA_SCALE), get(e, Display.DATA_RIGHT_ROT));
        assertArrayEquals(m, back, 1e-4F, "분해한 값을 다시 합치면 같은 행렬");
        assertArrayEquals(new float[] {1, 2, 3}, get(e, Display.DATA_TRANSLATION), 1e-6F);
    }

    @Test
    void axisAngleRotationIsAccepted() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:block_display");
        NbtCompound t = new NbtCompound();
        t.putList("translation", floats(0, 1, 0));
        NbtCompound axis = new NbtCompound();
        axis.putFloat("angle", (float) Math.PI / 2);
        axis.putList("axis", floats(0, 1, 0));
        t.putCompound("left_rotation", axis);
        t.putList("scale", floats(1, 1, 1));
        t.putList("right_rotation", floats(0, 0, 0, 1));
        in.putCompound("transformation", t);
        Entity e = Entities.load(level, in);
        float[] q = get(e, Display.DATA_LEFT_ROT);
        float s = (float) Math.sqrt(0.5);
        assertArrayEquals(new float[] {0, s, 0, s}, q, 1e-5F);
        assertArrayEquals(new float[] {0, 1, 0}, get(e, Display.DATA_TRANSLATION), 1e-6F);
    }

    @Test
    void brokenTransformationLeavesTheCurrentOneAndStartInterpolationIsRead() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:block_display");
        NbtCompound t = new NbtCompound();   // scale 이 없다: 바닐라 코덱 실패 → transformation 전체가 무시된다
        t.putList("translation", floats(7, 7, 7));
        t.putList("left_rotation", floats(0, 0, 0, 1));
        t.putList("right_rotation", floats(0, 0, 0, 1));
        in.putCompound("transformation", t);
        in.putInt("start_interpolation", 4);
        Entity e = Entities.load(level, in);
        assertArrayEquals(new float[] {0, 0, 0}, get(e, Display.DATA_TRANSLATION));
        assertEquals(4, e.entityData.get(Display.DATA_START_DELTA));
        // 15 개짜리 목록도 무시
        NbtCompound in2 = display("minecraft:block_display");
        in2.putList("transformation", floats(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 1));
        assertArrayEquals(new float[] {1, 1, 1}, get(Entities.load(level, in2), Display.DATA_SCALE));
    }

    @Test
    void textDisplayRoundTripsTextAlignmentAndFlags() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:text_display");
        in.putString("text", "{\"color\":\"red\",\"text\":\"Hello\"}");
        in.putString("alignment", "left");
        in.putBoolean("shadow", true);
        in.putBoolean("see_through", true);
        in.putInt("line_width", 120);
        in.putInt("background", 0x7F112233);
        in.putByte("text_opacity", (byte) 100);
        Display.TextDisplay d = (Display.TextDisplay) Entities.load(level, in);
        assertEquals("Hello", d.text().getString());
        assertEquals(Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH | Display.TextDisplay.FLAG_ALIGN_LEFT, d.flags());
        NbtCompound out = d.save();
        assertEquals("left", out.tags().get("alignment"));
        assertTrue(out.getBoolean("shadow"));
        assertTrue(out.getBoolean("see_through"));
        assertFalse(out.contains("default_background"));
        assertEquals(120, out.getIntOr0("line_width"));
        assertEquals("Hello", org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize((String) out.tags().get("text")).getString());
        // 오른쪽 정렬 + 기본 배경
        NbtCompound in2 = display("minecraft:text_display");
        in2.putString("alignment", "right");
        in2.putBoolean("default_background", true);
        Display.TextDisplay d2 = (Display.TextDisplay) Entities.load(level, in2);
        assertEquals(Display.TextDisplay.FLAG_ALIGN_RIGHT | Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND, d2.flags());
        assertEquals("right", d2.save().tags().get("alignment"));
        // 정렬이 없으면 가운데
        assertEquals("center", ((Display.TextDisplay) Entities.load(level, display("minecraft:text_display"))).save().tags().get("alignment"));
    }

    @Test
    void unparsableTextBecomesEmpty() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:text_display");
        in.putString("text", "{not json");
        Display.TextDisplay d = (Display.TextDisplay) Entities.load(level, in);
        assertEquals("", d.text().getString());
    }

    // ------------------------------------------------------------------ Interaction

    private static Player player(UUID id) {
        return new Player(21, id, "Steve", new ConcurrentEmbeddedChannel());
    }

    @Test
    void interactionRecordsAttackAndInteraction() {
        var level = new BlockPlacementTest.TestLevel();
        Interaction box = new Interaction(level, level.nextEntityId());
        UUID id = UUID.fromString("00000001-0000-0002-0000-000300000004");
        Player p = player(id);
        assertTrue(box.skipAttackInteraction(p), "response 가 꺼져 있으면 일반 공격 처리를 건너뛴다");
        assertTrue(box.interact(p));
        NbtCompound out = box.save();
        NbtCompound atk = (NbtCompound) out.tags().get("attack");
        assertArrayEquals(new int[] {1, 2, 3, 4}, (int[]) atk.tags().get("player"));
        assertEquals(level.gameTime(), atk.getLong("timestamp"));
        assertTrue(out.tags().get("interaction") instanceof NbtCompound);
        assertEquals(id, box.lastAttackPlayer());
        box.setResponse(true);
        assertFalse(box.skipAttackInteraction(p), "response 가 켜져 있으면 공격은 평소대로");
        // 왕복
        Interaction again = (Interaction) Entities.load(level, out);
        assertEquals(id, again.lastInteractionPlayer());
    }

    // ------------------------------------------------------------------ 효과 구름

    @Test
    void cloudParticleAndPotionContentsRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = display("minecraft:area_effect_cloud");
        NbtCompound particle = new NbtCompound();
        particle.putString("type", "minecraft:dust");
        particle.putList("color", floats(1.0F, 0.5F, 0.0F));
        particle.putFloat("scale", 2.0F);
        in.putCompound("Particle", particle);
        NbtCompound pc = new NbtCompound();
        pc.putString("potion", "minecraft:poison");
        in.putCompound("potion_contents", pc);
        AreaEffectCloud cloud = (AreaEffectCloud) Entities.load(level, in);
        assertEquals(new ParticleOptions.Dust(1.0F, 0.5F, 0.0F, 2.0F), cloud.getParticle());
        NbtCompound out = cloud.save();
        NbtCompound p2 = (NbtCompound) out.tags().get("Particle");
        assertEquals("minecraft:dust", p2.tags().get("type"));
        assertEquals("minecraft:poison", ((NbtCompound) out.tags().get("potion_contents")).tags().get("potion"));
    }

    @Test
    void cloudColoursEntityEffectParticleWithPotionColour() {
        var level = new BlockPlacementTest.TestLevel();
        AreaEffectCloud cloud = new AreaEffectCloud(level, level.nextEntityId());
        cloud.setPotionContents(null, java.util.List.of(new MobEffectInstance(MobEffect.POISON, 100, 0)), null);
        assertEquals(0xFF000000 | MobEffect.POISON.color(), ((ParticleOptions.Color) cloud.getParticle()).argb());
        // 비면 불투명 검정(바닐라 ARGB.opaque(0))
        AreaEffectCloud empty = new AreaEffectCloud(level, level.nextEntityId());
        empty.setPotionContents(null, java.util.List.of(), null);
        assertEquals(0xFF000000, ((ParticleOptions.Color) empty.getParticle()).argb());
        // 사용자 지정 색
        AreaEffectCloud custom = new AreaEffectCloud(level, level.nextEntityId());
        custom.setPotionContents(null, java.util.List.of(), 0x123456);
        assertEquals(0xFF123456, ((ParticleOptions.Color) custom.getParticle()).argb());
    }

    @Test
    void cloudSkipsCreaturesThatCannotBeAffected() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new org.domi.furnace.server.block.Pos(0, -61, 0), org.domi.furnace.server.block.B.defaultState("minecraft:stone"), 3);
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(zombie);
        var stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(stand);
        assertFalse(stand.isAffectedByPotions());
        AreaEffectCloud cloud = new AreaEffectCloud(level, level.nextEntityId());
        cloud.moveTo(0.5, -60, 0.5, 0, 0);
        cloud.setRadius(3.0F);
        cloud.setWaitTime(0);
        cloud.setEffects(java.util.List.of(new MobEffectInstance(MobEffect.POISON, 100, 0)));   // 언데드는 독에 안 걸린다
        level.addFreshEntity(cloud);
        for (int i = 0; i < 12; i++) {
            cloud.tickCount++;
            cloud.tick();
        }
        assertFalse(zombie.hasEffect(MobEffect.POISON));
        // 약화는 걸린다
        AreaEffectCloud cloud2 = new AreaEffectCloud(level, level.nextEntityId());
        cloud2.moveTo(0.5, -60, 0.5, 0, 0);
        cloud2.setRadius(3.0F);
        cloud2.setWaitTime(0);
        cloud2.setEffects(java.util.List.of(new MobEffectInstance(MobEffect.WEAKNESS, 100, 0)));
        level.addFreshEntity(cloud2);
        for (int i = 0; i < 12; i++) {
            cloud2.tickCount++;
            cloud2.tick();
        }
        assertTrue(zombie.hasEffect(MobEffect.WEAKNESS));
    }

    // ------------------------------------------------------------------ 엔더맨·TNT 수레·화살

    @Test
    void endermanTeleportsFromProjectilesWithoutTakingDamage() {
        var level = new ZombieCombatTest.ArenaLevel();
        var e = new Enderman(level, level.nextEntityId());
        e.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(e);
        float hp = e.health();
        double x0 = e.x(), z0 = e.z();
        e.hurtBy(new Entity.Damage("trident", false, false, false), 8.0F, null);   // is_projectile 태그
        assertEquals(hp, e.health(), "투사체는 맞지 않는다");
        assertTrue(e.x() != x0 || e.z() != z0 || e.y() != -60, "텔레포트했다");
    }

    @Test
    void fireArrowDetonatesTntMinecart() {
        var level = new ZombieCombatTest.ArenaLevel();
        MinecartTNT cart = (MinecartTNT) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.TNT);
        level.addFreshEntity(cart);
        var arrow = new Arrow(level, level.nextEntityId(), 0.5, -59, 0.5);
        arrow.setDeltaMovement(1.0, 0, 0);
        level.addFreshEntity(arrow);
        // 불이 없는 화살: 폭발하지 않는다
        cart.hurtByVia(new Entity.Damage("arrow", false, false, false), 2.0F, null, arrow);
        assertFalse(cart.isRemoved());
        arrow.igniteForSeconds(5.0F);
        cart.hurtByVia(new Entity.Damage("arrow", false, false, false), 2.0F, null, arrow);
        assertTrue(cart.isRemoved(), "불붙은 화살이 직접 원인이면 바로 터진다");
    }

    @Test
    void flyingFireArrowHitsAndDetonatesTntMinecart() {
        var level = new ZombieCombatTest.ArenaLevel();
        MinecartTNT cart = (MinecartTNT) AbstractMinecart.create(level, 4.5, -60, 0.5, AbstractMinecart.Type.TNT);
        level.addFreshEntity(cart);
        var arrow = new Arrow(level, level.nextEntityId(), 0.5, -59.6, 0.5);
        arrow.setDeltaMovement(1.5, 0, 0);
        arrow.igniteForSeconds(5.0F);
        level.addFreshEntity(arrow);
        for (int i = 0; i < 6 && !cart.isRemoved(); i++) {
            level.time++;
            arrow.tickCount++;
            arrow.tick();
        }
        assertTrue(cart.isRemoved(), "날아가던 불화살이 TNT 수레를 맞혀 터뜨렸다");
        assertTrue(arrow.isRemoved());
    }

    @Test
    void arrowHitCountsAsStuckArrowAndWearsOffOverTime() {
        var level = new ZombieCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(z);
        var a = new Arrow(level, level.nextEntityId(), 0.5, -59, 0.5);
        a.setDeltaMovement(1.0, 0, 0);
        level.addFreshEntity(a);
        a.onHitEntity(new Projectile.Hit(z, null, 0, 0.5, -59, 0.5));
        assertEquals(1, z.getArrowCount());
        assertTrue(a.isRemoved());
        // 20 × (30 − 1) = 580 틱 뒤 빠진다
        for (int i = 0; i < 579; i++) {
            level.time++;
            z.tickCount++;
            z.tick();
        }
        assertEquals(1, z.getArrowCount());
        level.time++;
        z.tickCount++;
        z.tick();
        assertEquals(0, z.getArrowCount());
    }

    @Test
    void armorStandItemKeepsCustomName() {
        var level = new ZombieCombatTest.ArenaLevel();
        var stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(0.5, -60, 0.5, 0, 0);
        stand.setCustomName(Text.text("Bob"));
        level.addFreshEntity(stand);
        var p = player(UUID.randomUUID());
        var dmg = new Entity.Damage("player_attack", false, false, false);
        stand.hurtBy(dmg, 1.0F, p);
        level.time = 3;
        stand.hurtBy(dmg, 1.0F, p);
        assertTrue(stand.isRemoved());
        boolean found = false;
        for (Entity e : level.entities().all()) {
            if (e instanceof ItemEntity ie && org.domi.furnace.server.item.ItemTools.itemName(ie.getItem()).equals("minecraft:armor_stand")) {
                found = org.domi.furnace.server.item.ItemTools.component(ie.getItem(), "minecraft:custom_name") instanceof String s && s.contains("Bob");
            }
        }
        assertTrue(found, "거치대 아이템에 커스텀 이름");
    }
}
