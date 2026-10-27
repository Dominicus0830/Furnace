// 바닐라 SculkCatalystBlockEntity + CatalystListener: 반경 8 안의 entity_die 를 가까운 순(BY_DISTANCE)으로 듣고,
// 경험치를 아직 안 쓴 생물이면 그 경험치만큼 SculkSpreader 에 전하를 넣고(경험치 구슬은 안 떨어진다) bloom 을 켠다.
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
import org.domi.furnace.server.entity.LivingEntity;
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
        // TODO(입자): sendParticles(SCULK_SOUL, x+0.5, y+1.15, z+0.5, 2, 0.2, 0, 0.2, 0) — 서버 난수는 쓰지 않는다.
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
            if (event != GameEvent.ENTITY_DIE || !(context.sourceEntity() instanceof LivingEntity living)) return false;
            if (!living.wasExperienceConsumed()) {
                int i = living.experienceReward();
                if (living.dropsExperience() && i > 0) {
                    // BlockPos.containing(pos.relative(UP, 0.5))
                    sculkSpreader.addCursors(new Pos((int) Math.floor(pos.x()), (int) Math.floor(pos.y() + 0.5), (int) Math.floor(pos.z())), i);
                    // TODO(발전 과제): kill_mob_near_sculk_catalyst — 처치 트리거들이 아직 연결돼 있지 않다.
                }
                living.skipDropExperience();
                Optional<Vec3> p = positionSource.getPosition(level);
                p.ifPresent(v -> bloom(level, new Pos((int) Math.floor(v.x()), (int) Math.floor(v.y()), (int) Math.floor(v.z())), blockState,
                        level.random()));
            }
            return true;
        }
    }
}
