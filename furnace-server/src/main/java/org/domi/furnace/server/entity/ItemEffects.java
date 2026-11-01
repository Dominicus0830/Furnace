// 아이템 사용 효과 몇 가지(바닐라 Item.finishUsingItem / interactLivingEntity): 합창 열매 순간이동, 불길한 병, 이름표.
// 플레이어 쪽만 다룬다(여우가 합창 열매를 먹는 경우는 아직 없다). region 스레드에서만 부른다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;

public final class ItemEffects {
    private ItemEffects() { }

    // ------------------------------------------------------------------ 염소 뿔(GoatHornItem)

    /** Instrument(sound_event, use_duration 틱, range). */
    public record Instrument(String sound, int useDuration, float range) { }

    /** 염소 뿔 악기 8종(Instruments.bootstrap): 소리는 item.goat_horn.sound.N, 사용 140틱, 범위 256. */
    private static final String[] GOAT_HORNS = {"ponder", "sing", "seek", "feel", "admire", "call", "yearn", "dream"};

    /**
     * getInstrument(stack): instrument 컴포넌트(이름 또는 직접 정의), 없으면 goat_horns 태그의 첫 번째(ponder_goat_horn).
     * 모르는 이름이면 null(FAIL).
     */
    public static Instrument instrumentOf(ItemStack horn) {
        Object c = ItemTools.component(horn, "minecraft:instrument");
        if (c == null) return goatHorn(0);
        if (c instanceof String id) {
            for (int i = 0; i < GOAT_HORNS.length; i++) if (id.equals("minecraft:" + GOAT_HORNS[i] + "_goat_horn")) return goatHorn(i);
            return null;
        }
        if (c instanceof java.util.Map<?, ?> m) {
            Object se = m.get("sound_event");
            String sound = se instanceof String s ? s : se instanceof java.util.Map<?, ?> sm ? String.valueOf(sm.get("sound_id")) : null;
            if (sound == null) return null;
            int dur = m.get("use_duration") instanceof Number n ? n.intValue() : 0;
            float range = m.get("range") instanceof Number n ? n.floatValue() : 0.0F;
            return new Instrument(sound, dur, range);
        }
        return null;
    }

    private static Instrument goatHorn(int i) { return new Instrument("minecraft:item.goat_horn.sound." + i, 140, 256.0F); }

    // ------------------------------------------------------------------ 합창 열매(ChorusFruitItem.finishUsingItem 의 순간이동 부분)

    /**
     * 합창 열매를 먹은 뒤: 최대 16번 시도한다. 시도마다 플레이어 난수로 x(±8 실수) → y(정수 -8..7, 높이 범위로 고정) → z(±8 실수)를 뽑고,
     * 탈것에서 내린 뒤 randomTeleport(발밑이 단단해질 때까지 내려가 몸이 들어맞는지)를 본다. 성공하면 teleport 게임 이벤트(옛 위치)·소리·낙하 거리 초기화로 끝.
     * 끝나면 (성공 여부와 무관하게) 충격 맥락을 지우고 20틱 재사용 대기.
     * 차이: 서버가 플레이어를 옮기는 일은 연결 스레드로 넘어가므로(Player.teleportTo) 몸이 들어맞는지는 후보 위치로 옮긴 상자로 본다(바닐라는 옮겼다 되돌린다).
     */
    public static void chorusFruitTeleport(Player p, ChunkLevel level) {
        var r = p.random();
        int min = level.minBuildHeight(), max = min + level.dimensionType().logicalHeight() - 1;
        for (int i = 0; i < 16; i++) {
            double d0 = p.x() + (r.nextDouble() - 0.5) * 16.0;
            double d1 = Math.clamp(p.y() + (double) (r.nextInt(16) - 8), (double) min, (double) max);
            double d2 = p.z() + (r.nextDouble() - 0.5) * 16.0;
            if (p.isPassenger()) p.stopRiding();
            Vec3 old = new Vec3(p.x(), p.y(), p.z());
            double[] to = randomTeleportTarget(p, level, d0, d1, d2);
            if (to != null) {
                p.teleportTo(to[0], to[1], to[2]);
                var ev = new EntityEvent(p.entityId(), (byte) 46);   // 순간이동 입자
                p.send(ev);
                p.vitals().broadcastToTrackers(ev);
                level.gameEvent(GameEvent.TELEPORT, old, GameEventContext.of(p));
                level.playSound(null, to[0], to[1], to[2], "minecraft:item.chorus_fruit.teleport", "players", 1.0F, 1.0F);
                p.vitals().resetFallDistance();
                break;
            }
        }
        p.vitals().resetCurrentImpulseContext();
        p.addCooldown(ItemContext.id("minecraft:item", "chorus_fruit"), 20);
    }

    /** Entity.randomTeleport 의 판정: 가로(nx,nz)에서 발밑이 움직임을 막는 블록일 때까지 내려가 상자가 비었고 액체가 없으면 {x, y, z}, 아니면 null. */
    public static double[] randomTeleportTarget(Player p, ChunkLevel level, double nx, double ny, double nz) {
        Pos pos = new Pos((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
        if (!level.hasChunkAt(pos)) return null;
        double d3 = ny;
        boolean found = false;
        while (!found && pos.y() > level.minBuildHeight()) {
            Pos below = pos.below();
            if (BlockInfo.get().blocksMotion(level.getBlockState(below))) {
                found = true;
            } else {
                d3--;
                pos = below;
            }
        }
        if (!found) return null;
        AABB box = p.boundingBox().move(nx - p.x(), d3 - p.y(), nz - p.z());
        return level.noCollision(box) && !level.containsAnyLiquid(box) ? new double[] {nx, d3, nz} : null;
    }

    // ------------------------------------------------------------------ 불길한 병(OminousBottleItem.finishUsingItem)

    /** 사용 시간(틱). */
    public static final int OMINOUS_BOTTLE_TICKS = 32;
    public static final int BAD_OMEN_TICKS = 120000;

    /**
     * 마시기를 마쳤을 때: 소모는 호출한 쪽이. 소리(플레이어 기준 칸에서 모두에게) 뒤 불길한 징조를 component 증폭(0~4)으로 12만 틱,
     * 아이콘만 보이게(앰비언트·입자 없음) 건다. 음식 컴포넌트(영양 1)는 쓰이지 않는다(finishUsingItem 이 super 를 부르지 않는다).
     */
    public static void ominousBottleDrunk(Player p, ChunkLevel level, ItemStack bottle) {
        if (level != null) {   // 소리만 레벨이 필요하다. 나쁜 징조는 레벨 없이도 건다
            Pos pos = new Pos((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
            level.playSound(null, pos, "minecraft:item.ominous_bottle.dispose", "players", 1.0F, 1.0F);
        }
        int amplifier = ItemTools.component(bottle, "minecraft:ominous_bottle_amplifier") instanceof Number n ? n.intValue() : 0;
        p.vitals().addEffect(new MobEffectInstance(MobEffect.byName("minecraft:bad_omen"), BAD_OMEN_TICKS, amplifier, false, false, true));
    }

    // ------------------------------------------------------------------ 이름표(NameTagItem.interactLivingEntity)

    /**
     * 이름표(custom_name 이 있는 것)를 플레이어가 아닌 생물에게: 살아 있으면 이름을 붙이고(몹은 사라지지 않게), 이름표를 하나 쓴다. 크리에이티브는 복사본을 쓰므로 줄지 않는다.
     * 이름이 없거나 플레이어면 넘긴다(false). 이름이 있으면 죽은 대상이어도 소비된 것으로 본다(true — 서버는 CONSUME).
     */
    public static boolean nameTag(Player p, int hand, ItemStack held, LivingEntity target) {
        if (held.isEmpty() || !ItemTools.itemName(held).equals("minecraft:name_tag")) return false;
        if (!(ItemTools.component(held, "minecraft:custom_name") instanceof String json)) return false;
        if (target instanceof Mob m0 && m0.getLeashHolder() == p) return false;   // Mob.interact: 내가 맨 끈 풀기가 이름표보다 먼저(끈 맺기 lead 는 이름표가 앞선다)
        if (target instanceof Mob && !target.isAlive()) return false;   // Mob.interact: 죽었으면 PASS
        if (target.isAlive()) {
            try {
                target.setCustomName(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json));
            } catch (RuntimeException e) {
                return false;   // 읽을 수 없는 이름은 컴포넌트 자체가 만들어질 수 없다
            }
            if (target instanceof Mob m) m.setPersistenceRequired();
            if (!p.vitals().isCreative()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            }
        }
        target.level().gameEvent(GameEvent.ENTITY_INTERACT, new Vec3(target.x(), target.y(), target.z()), GameEventContext.of(p));
        return true;
    }
}
