// 바닐라 EndGatewayBlock: 엔드 관문. 쿨다운이 아닐 때 들어온 엔티티를 포털 대기에 걸고 쿨다운을 건다. 대기가 차면(0틱 — 바로)
// 같은 레벨의 출구로 보낸다: 위치는 TheEndGatewayBlockEntity.getPortalPosition 의 바닥 가운데, 속도는 그대로(엔더 진주는 (0, -1, 0)),
// 회전은 그대로, 도착 칸에 포털 티켓(PLACE_PORTAL_TICKET).
// Furnace 는 출구 쪽 청크가 다른 region 스레드 소유이고 아직 없을 수 있어 청크를 비동기로 올린 뒤 그 region 스레드에서 계산한다.
package org.domi.furnace.server.block.types;

import java.util.function.BiConsumer;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.EndGatewayBlockEntity;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Portal;
import org.domi.furnace.server.entity.PortalTravel;
import org.domi.furnace.server.entity.Traveler;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EndGatewayBlock.class);
    /** 출구를 만들 때 walk 가 멈춘 청크 둘레로 올릴 반경(블록): 섬(±7)·가장 높은 블록(±16)·관문(±1)이 다 들어간다. */
    private static final int BUILD_RADIUS = 32;

    public EndGatewayBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new EndGatewayBlockEntity(pos, state); }

    /** canBeReplaced(state, fluid): 언제나 거짓. */
    @Override
    public boolean canBeReplaced(int state, int fluidType) { return false; }

    /** entityInside: 포털을 쓸 수 있고 관문이 쿨다운이 아니면 포털 대기에 걸고 triggerCooldown. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, Entity entity) {
        if (entity.canUsePortal(false) && level.getBlockEntity(pos) instanceof EndGatewayBlockEntity g && !g.isCoolingDown()) {
            entity.setAsInsidePortal(this, pos);
            g.triggerCooldown(level);
        }
    }

    /** 플레이어판(Region 이 canUsePortal 을 먼저 본다). */
    public void entityInsidePlayer(BlockLevel level, Pos pos, Player p) {
        if (level.getBlockEntity(pos) instanceof EndGatewayBlockEntity g && !g.isCoolingDown()) {
            p.setAsInsidePortal(this, pos);
            g.triggerCooldown(level);
        }
    }

    @Override
    public boolean withinLevel() { return true; }

    /** 다른 차원으로는 보내지 않는다. */
    @Override
    public PortalTravel prepareTravel(ChunkLevel level, Traveler traveler, Pos pos) { return null; }

    /** getPortalDestination 의 동기판(시험 레벨). */
    @Override
    public DimensionTransition getPortalDestination(ChunkLevel level, Entity entity, Pos pos) {
        if (!(level.getBlockEntity(pos) instanceof EndGatewayBlockEntity g)) return null;
        Pos p = g.getPortalPosition(level);
        return p == null ? null : transition(level, p, Traveler.of(entity));
    }

    /**
     * 서버판: 출구(없으면 만들고 원천 블록 엔티티에 기록)를 정한 뒤 출구 둘레 청크를 올려 그 region 스레드에서 도착 칸을 찾는다.
     * 원천 레벨 스레드에서 부른다.
     */
    @Override
    public void travelWithinLevel(ChunkLevel level, Pos pos, Traveler t, BiConsumer<ChunkLevel, DimensionTransition> arrive) {
        LevelServices.DimensionTravel travel = level.dimensionTravel();
        if (travel == null || !(level.getBlockEntity(pos) instanceof EndGatewayBlockEntity g)) {
            arrive.accept(null, null);
            return;
        }
        boolean exact = g.exactTeleport();
        g.withExit(level, exit -> {
            if (exit == null) {
                arrive.accept(null, null);
                return;
            }
            boolean ex = g.exitPortal() != null ? g.exactTeleport() : exact;
            travel.withLoadedChunks(exit.x(), exit.z(), ex ? 0 : 5, lvl -> {
                if (lvl == null) {
                    arrive.accept(null, null);
                    return;
                }
                DimensionTransition d;
                try {
                    d = transition(lvl, ex ? exit : EndGatewayBlockEntity.findExitPosition(lvl, exit), t);
                } catch (RuntimeException e) {
                    log.error("[GATEWAY] 도착 칸 계산 실패 {}", exit, e);
                    d = null;
                }
                arrive.accept(d == null ? null : lvl, d);
            });
        }, () -> makeExit(travel, pos, g));
    }

    /** 출구 만들기(findOrCreateValidTeleportPos → 10 위에 관문): 청크를 하나씩 올리며 walk 를 진행하고, 끝나면 원천 칸 region 에 돌려준다. */
    private static void makeExit(LevelServices.DimensionTravel travel, Pos gateway, EndGatewayBlockEntity waiting) {
        EndGatewayBlockEntity.ExitWalk walk = new EndGatewayBlockEntity.ExitWalk(gateway);
        walkStep(travel, gateway, walk, exit -> travel.runAtSource(gateway.x(), gateway.z(), src -> {
            EndGatewayBlockEntity self = src != null && src.getBlockEntity(gateway) instanceof EndGatewayBlockEntity g ? g : null;
            waiting.finishExit(src, self, exit);
        }));
    }

    private static void walkStep(LevelServices.DimensionTravel travel, Pos gateway, EndGatewayBlockEntity.ExitWalk walk,
                                 java.util.function.Consumer<Pos> done) {
        int cx = walk.chunkX(), cz = walk.chunkZ();
        travel.withLoadedChunks(cx << 4, cz << 4, 0, lvl -> {
            if (lvl == null) {
                done.accept(null);
                return;
            }
            boolean finished;
            try {
                finished = walk.feed(EndGatewayBlockEntity.isChunkEmpty(lvl, cx, cz));
            } catch (RuntimeException e) {
                log.error("[GATEWAY] 출구 청크 찾기 실패 {}", gateway, e);
                done.accept(null);
                return;
            }
            if (!finished) {
                walkStep(travel, gateway, walk, done);
                return;
            }
            int fx = walk.chunkX(), fz = walk.chunkZ();
            travel.withLoadedChunks((fx << 4) + 8, (fz << 4) + 8, BUILD_RADIUS, lvl2 -> {
                if (lvl2 == null) {
                    done.accept(null);
                    return;
                }
                Pos exit;
                try {
                    exit = EndGatewayBlockEntity.buildExit(lvl2, walk, gateway);
                } catch (RuntimeException e) {
                    log.error("[GATEWAY] 출구 관문 만들기 실패 {}", gateway, e);
                    exit = null;
                }
                done.accept(exit);
            });
        });
    }

    /**
     * DimensionTransition(level, p.getBottomCenter(), 엔더 진주면 (0, -1, 0) 아니면 지금 속도, yRot, xRot, PLACE_PORTAL_TICKET):
     * 소리 없음, 도착 칸에 포털 티켓.
     */
    static DimensionTransition transition(ChunkLevel level, Pos p, Traveler t) {
        double x = p.x() + 0.5, y = p.y(), z = p.z() + 0.5;
        double vx = t.enderPearl() ? 0.0 : t.vx(), vy = t.enderPearl() ? -1.0 : t.vy(), vz = t.enderPearl() ? 0.0 : t.vz();
        return new DimensionTransition(level, x, y, z, vx, vy, vz, t.yRot(), t.xRot(), false,
                new Pos(Entity.floor(x), Entity.floor(y), Entity.floor(z)), false);
    }
}
