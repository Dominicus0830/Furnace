// 바닐라 BrushItem: 솔(brush)을 블록에 대고 오래 쓴다. useOn 이 사용을 시작하고(200틱, CONSUME), 매 틱 onUseTick 이 10틱마다(5번째 틱)
// 솔질 한 번을 한다: 시선 판정(블록·엔티티) → (파티클용 난수) → 소리 → 블록 엔티티 brush → 다 닦이면 솔 내구도 1.
// 파티클은 클라이언트 쪽이지만 서버도 같은 난수를 뽑는다(레벨 난수: nextIntBetweenInclusive(7,12) + 반복마다 nextDouble 2번).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBreaking;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.types.BrushableBlock;
import org.domi.furnace.server.block.entity.BrushableBlockEntity;

public final class Brushing {
    private Brushing() { }

    public static final String ITEM = "minecraft:brush";
    /** 사용 시간(getUseDuration)과 솔질 간격(틱). */
    public static final int USE_DURATION = 200, BRUSH_INTERVAL = 10;

    public static boolean isBrush(ItemStack s) { return org.domi.furnace.server.item.ItemTools.itemName(s).equals(ITEM); }

    /**
     * calculateHitResult(player) = ProjectileUtil.getHitResultOnViewVector(player, !spectator && pickable, blockInteractionRange):
     * 눈에서 시선 방향으로 블록 판정(COLLIDER + 플레이어 문맥, 유체 무시 — 풀·횃불처럼 충돌 모양이 없는 블록은 지나간다) 후, 그 끝점까지 확장 상자 안 엔티티(여유 0)가 먼저 맞으면 엔티티 맞음.
     * 블록에 맞았으면 그 결과, 아니면(빗나감·엔티티가 앞) null. 시선은 현재 회전(바닐라는 xRotO/yRotO — 틱 시작 값과 같다고 가정).
     */
    public static Clip.Result calculateBlockHit(ChunkLevel level, BlockActor actor, Player p) {
        double range = p.attributes().value(Attribute.BLOCK_INTERACTION_RANGE) + (actor.isCreative() ? 0.5 : 0.0);
        double[] v = Clip.viewVector(actor.xRot(), actor.yRot());
        double vx = v[0] * range, vy = v[1] * range, vz = v[2] * range;
        double ex = actor.x(), ey = actor.y() + actor.eyeHeight(), ez = actor.z();
        double tx = ex + vx, ty = ey + vy, tz = ez + vz;
        Clip.Result hit = Clip.clipCollider(level, org.domi.furnace.server.block.CollisionContext.of(p), ex, ey, ez, tx, ty, tz, Clip.FluidMode.NONE);
        if (!hit.miss()) {
            tx = hit.x();
            ty = hit.y();
            tz = hit.z();
        }
        var box = p.boundingBox().expandTowards(vx, vy, vz).inflate(1.0);
        final double fx = tx, fy = ty, fz = tz;
        boolean[] entity = {false};
        for (var e : level.getEntities(null, box, x -> !x.isSpectator() && x.isPickable())) {
            if (e.boundingBox().clip(ex, ey, ez, tx, ty, tz) != null) entity[0] = true;
        }
        level.forEachPlayerIn(box, o -> {   // 다른 플레이어도 시선을 가린다(자신 제외)
            if (o != p && !o.isSpectator() && o.isPickable() && o.boundingBox().clip(ex, ey, ez, fx, fy, fz) != null) entity[0] = true;
        });
        return entity[0] || hit.miss() ? null : hit;
    }

    /** BrushItem.useOn: 시선이 블록에 맞으면 사용을 시작한다. 늘 CONSUME. */
    public static InteractionResult useOn(ChunkLevel level, BlockActor actor, int hand) {
        if (actor.handle() instanceof Player p && calculateBlockHit(level, actor, p) != null) actor.startUsingItem(hand, USE_DURATION);
        return InteractionResult.CONSUME;
    }

    /**
     * onUseTick(level, entity, stack, remainingUseDuration): 시선이 블록에 안 맞으면 사용을 놓는다(false 반환 = 그만). 경과 틱 % 10 == 5 일 때 솔질.
     * 반환이 true 면 계속 쓴다.
     */
    public static boolean onUseTick(ChunkLevel level, BlockActor actor, Player p, int hand, int remaining) {
        if (remaining < 0) return false;
        Clip.Result hit = calculateBlockHit(level, actor, p);
        if (hit == null) return false;
        int elapsed = USE_DURATION - remaining + 1;
        if (elapsed % BRUSH_INTERVAL != 5) return true;
        int state = level.getBlockState(hit.pos());
        if (spawnsDust(state)) {   // 파티클은 보내지 않지만 서버 난수는 소모한다(spawnDustParticles)
            var r = level.random();
            int n = 7 + r.nextInt(6);   // nextIntBetweenInclusive(7, 12)
            for (int i = 0; i < n; i++) {
                r.nextDouble();   // 속도 x
                r.nextDouble();   // 속도 z
            }
        }
        String sound = B.behavior(state) instanceof BrushableBlock bb ? bb.brushSound() : "minecraft:item.brush.brushing.generic";
        level.playSound(actor, hit.pos(), sound, "blocks", 1.0F, 1.0F);
        if (level.getBlockEntity(hit.pos()) instanceof BrushableBlockEntity be && be.brush(level, level.gameTime(), actor, hit.face())) {
            BlockBreaking.hurtAndBreak(level, actor, hand, actor.itemInHand(hand), 1);
        }
        return true;
    }

    /**
     * shouldSpawnTerrainParticles && renderShape != INVISIBLE: 안 보이는 블록(공기·유체·표지판·깃발·머리·장벽·빛·구조 빈 칸·움직이는 피스톤·
     * 엔드 차원문/관문·장식 항아리·거품 기둥)이거나 지형 파티클이 꺼진 블록(장벽·구조 빈 칸)이면 거짓.
     */
    public static boolean spawnsDust(int state) {
        String n = org.domi.furnace.server.block.Blocks.get().name(B.block(state));
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        if (n.endsWith("_air") || n.equals("air") || n.equals("water") || n.equals("lava")) return false;
        if (n.endsWith("_sign") || n.endsWith("_banner") || n.endsWith("_skull") || n.endsWith("_head") && !n.equals("piston_head")) return false;
        return switch (n) {
            case "barrier", "light", "structure_void", "moving_piston", "end_portal", "end_gateway", "decorated_pot", "bubble_column" -> false;
            default -> true;
        };
    }
}
