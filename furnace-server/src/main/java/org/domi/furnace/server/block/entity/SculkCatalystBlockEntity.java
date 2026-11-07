// 바닐라 SculkCatalystBlockEntity + CatalystListener: 반경 8 안의 entity_die 를 가까운 순(BY_DISTANCE)으로 듣고,
// 경험치를 아직 안 쓴 생물(플레이어 포함 — 레벨×7, 최대 100)이면 그 경험치만큼 SculkSpreader 에 전하를 넣고(경험치 구슬은 안 떨어진다) bloom 을 켠다.
// 매 틱 커서를 갱신하고, 커서 목록은 블록 엔티티 NBT "cursors" 에 저장한다.
package org.domi.furnace.server.block.entity;

import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.types.SculkSpreader;
import org.domi.furnace.server.entity.KillCredit;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.GameEventListener;
import org.domi.furnace.server.world.gameevent.PositionSource;

public class SculkCatalystBlockEntity extends BlockEntity implements GameEventListener.Provider {

    private static final int CATALYST = B.id("sculk_catalyst");
    private static final Prop PULSE = B.prop("bloom");

    private final CatalystListener catalystListener;

    public SculkCatalystBlockEntity(Pos pos, int state) {
        super("minecraft:sculk_catalyst", pos, state);
        this.catalystListener = new CatalystListener(state, PositionSource.block(pos));
    }

    @Override
    public GameEventListener getListener() { return catalystListener; }

    public SculkSpreader getSculkSpreader() { return catalystListener.sculkSpreader; }

    @Override
    public boolean ticking() { return true; }

    /** serverTick: updateCursors(level, pos, level.random, true). */
    @Override
    public void tick(BlockLevel level) {
        if (level instanceof ChunkLevel cl) catalystListener.sculkSpreader.updateCursors(level, pos, cl.random(), true);
    }

    @Override
    public boolean isValid(int state) { return B.block(state) == CATALYST; }

    @Override
    public void load(NbtCompound tag) { catalystListener.sculkSpreader.load(tag); }

    @Override
    protected void saveAdditional(NbtCompound tag) { catalystListener.sculkSpreader.save(tag); }

    /** SculkCatalystBlock.bloom: bloom 켜고 8틱 뒤 끄기 예약, 영혼 입자 2개, 소리. */
    public static void bloom(ChunkLevel level, Pos pos, int state, RandomSource random) {
        level.setBlock(pos, PULSE.with(state, true), 3);
        level.scheduleTick(pos, B.block(state), 8);
        level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("sculk_soul"), pos.x() + 0.5, pos.y() + 1.15, pos.z() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);   // 서버 난수는 쓰지 않는다
        level.playSound(null, pos, "minecraft:block.sculk_catalyst.bloom", "blocks", 2.0F, 0.6F + random.nextFloat() * 0.4F);
    }

    public static final class CatalystListener implements GameEventListener {
        public static final int PULSE_TICKS = 8;
        final SculkSpreader sculkSpreader = SculkSpreader.createLevelSpreader();
        private final int blockState;
        private final PositionSource positionSource;

        CatalystListener(int blockState, PositionSource positionSource) {
            this.blockState = blockState;
            this.positionSource = positionSource;
        }

        @Override
        public PositionSource getListenerSource() { return positionSource; }

        @Override
        public int getListenerRadius() { return 8; }

        @Override
        public DeliveryMode getDeliveryMode() { return DeliveryMode.BY_DISTANCE; }

        @Override
        public boolean handleGameEvent(ChunkLevel level, GameEvent event, GameEventContext context, Vec3 pos) {
            if (event != GameEvent.ENTITY_DIE) return false;
            Object source = context.sourceEntity();
            if (source instanceof LivingEntity living) {
                if (!living.wasExperienceConsumed()) {
                    int i = living.experienceReward();
                    if (living.dropsExperience() && i > 0) {
                        // BlockPos.containing(pos.relative(UP, 0.5))
                        sculkSpreader.addCursors(new Pos((int) Math.floor(pos.x()), (int) Math.floor(pos.y() + 0.5), (int) Math.floor(pos.z())), i);
                        // 바닐라: 킬 크레딧이 플레이어면 kill_mob_near_sculk_catalyst(죽은 몹, 마지막 피해 — 없으면 player_attack)
                        if (living.getKillCredit() instanceof Player killer) {
                            String type = living.getLastDamageType() == null ? "player_attack" : living.getLastDamageType();
                            var attacker = living.getLastDamageAttacker();
                            var causing = living.getLastDamageType() == null ? killer
                                    : attacker instanceof Rider r ? r : null;
                            awardItSpreads(killer, KillCredit.lootOf(living), KillCredit.source(type, causing, causing));
                        }
                    }
                    living.skipDropExperience();
                    bloomAt(level);
                }
                return true;
            }
            if (source instanceof Player dying) {
                // Player 도 LivingEntity 다: Player.getBaseExperienceReward(레벨×7, 최대 100 — keepInventory·관전자면 0), shouldDropExperience = !isBaby = true
                var vitals = dying.vitals();
                if (!vitals.wasExperienceConsumed()) {
                    int i = vitals.baseExperienceReward();
                    if (i > 0) {
                        sculkSpreader.addCursors(new Pos((int) Math.floor(pos.x()), (int) Math.floor(pos.y() + 0.5), (int) Math.floor(pos.z())), i);
                        if (dying.getKillCredit() instanceof Player killer) {
                            // getLastDamageSource() — 방금 죽은 피해(없으면 player_attack)
                            String type = vitals.deathType() == null ? "player_attack" : vitals.deathType();
                            Rider causing = vitals.deathType() == null ? killer : vitals.deathCausing();
                            Rider direct = vitals.deathType() == null ? killer : vitals.deathDirect();
                            awardItSpreads(killer, KillCredit.lootOf(dying), KillCredit.source(type, causing, direct));
                        }
                    }
                    vitals.skipDropExperience();
                    bloomAt(level);
                }
                return true;
            }
            return false;
        }

        /** tryAwardItSpreadsAdvancement: kill_mob_near_sculk_catalyst(죽은 쪽, 피해 원인). */
        private static void awardItSpreads(Player killer, org.domi.furnace.server.loot.LootEntity dead, org.domi.furnace.server.loot.LootDamageSource src) {
            killer.triggerAdvancement(a -> a.triggerKill("minecraft:kill_mob_near_sculk_catalyst", dead, src));
        }

        private void bloomAt(ChunkLevel level) {
            Optional<Vec3> p = positionSource.getPosition(level);
            p.ifPresent(v -> bloom(level, new Pos((int) Math.floor(v.x()), (int) Math.floor(v.y()), (int) Math.floor(v.z())), blockState,
                    level.random()));
        }
    }
}
