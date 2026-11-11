// 바닐라 LeashFenceKnotEntity: 울타리에 묶는 밧줄 매듭. 울타리 칸에 붙어 있고, 울타리가 없어지면 떨어진다(100틱마다 확인).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class LeashFenceKnotEntity extends Entity {

    /** BlockAttachedEntity.thunderHit: 번개에 맞아도 아무 일 없다. */
    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {}

    private Pos blockPos;
    private int checkInterval;

    public LeashFenceKnotEntity(ChunkLevel level, int id) { super(EntityType.LEASH_KNOT, level, id); }

    public LeashFenceKnotEntity(ChunkLevel level, int id, Pos pos) {
        this(level, id);
        setPos(pos.x(), pos.y(), pos.z());
    }

    @Override
    protected AABB makeBb(double px, double py, double pz) {
        double hw = 0.375 / 2.0;
        return new AABB(px - hw, py, pz - hw, px + hw, py + 0.5, pz + hw);
    }

    /** setPos: 칸 가운데, 높이는 칸 아래에서 0.375. */
    @Override
    public void setPos(double nx, double ny, double nz) {
        blockPos = new Pos((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
        double cx = blockPos.x() + 0.5, cy = blockPos.y() + 0.375, cz = blockPos.z() + 0.5;
        setPosRaw(cx, cy, cz);
        refreshBoundingBox();
    }

    public Pos pos() { return blockPos; }

    @Override public boolean isPickable() { return true; }

    @Override
    public void tick() {
        if (checkInterval++ == 100) {
            checkInterval = 0;
            if (!isRemoved() && !survives()) discard();
        }
    }

    public boolean survives() { return B.isTag(level.getBlockState(blockPos), "minecraft:fences"); }

    /** LeashFenceKnotEntity.getOrCreateKnot: 그 칸에 이미 있으면 그것, 없으면 만들어 넣는다. */
    public static LeashFenceKnotEntity getOrCreateKnot(ChunkLevel level, Pos pos) {
        for (LeashFenceKnotEntity k : level.getEntitiesOfClass(LeashFenceKnotEntity.class,
                new AABB(pos.x() - 1.0, pos.y() - 1.0, pos.z() - 1.0, pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0), t -> true)) {
            if (k.blockPos.equals(pos)) return k;
        }
        LeashFenceKnotEntity knot = new LeashFenceKnotEntity(level, level.nextEntityId(), pos);
        level.addFreshEntity(knot);
        return knot;
    }

    @Override public int spawnData() { return 0; }

    /** BlockAttachedEntity.hurt: 맞으면 죽고(매인 몹은 다음 틱에 끈이 풀려 끈 아이템을 떨군다) 깨지는 소리. */
    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if (!isRemoved()) {
            kill();
            markHurt();
            playSound("entity.leash_knot.break", 1.0F, 1.0F);
        }
        return true;
    }

    @Override public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    @Override
    public boolean skipAttackInteraction(Player attacker) {
        if (attacker.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return true;
        hurtBy(Damage.playerAttack(attacker), 0.0F, attacker);
        return true;
    }

    public void playPlacementSound() { playSound("entity.leash_knot.place", 1.0F, 1.0F); }

    /** LeashableUtil.leashableInArea(cuz.a): 칸 둘레 ±7 안에서 조건에 맞는 끈 달린 몹. */
    public static java.util.List<Leashable> leashableInArea(ChunkLevel level, Pos pos, java.util.function.Predicate<Leashable> test) {
        AABB box = new AABB(pos.x() - 7.0, pos.y() - 7.0, pos.z() - 7.0, pos.x() + 7.0, pos.y() + 7.0, pos.z() + 7.0);
        var out = new java.util.ArrayList<Leashable>();
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, x -> x instanceof Leashable l && test.test(l))) out.add((Leashable) e);
        return out;
    }

    /** LeadItem.bindPlayerMobs(cuz.a): 플레이어가 끌던 몹을 이 칸 매듭에 묶는다. 하나라도 묶으면 true. */
    public static boolean bindPlayerMobs(Player player, ChunkLevel level, Pos pos, Object gameEventSource) {
        LeashFenceKnotEntity knot = null;
        var list = leashableInArea(level, pos, m -> m.getLeashHolder() == player);
        for (Leashable m : list) {
            if (knot == null) {
                knot = getOrCreateKnot(level, pos);
                knot.playPlacementSound();
            }
            m.setLeashedTo(knot, true);
        }
        return !list.isEmpty();
    }

    /** LeashFenceKnotEntity.interact(cjb:46): 내 끈을 매듭에 옮기고, 없으면 매듭을 없앤다(크리에이티브는 매인 것도 푼다). */
    public void interact(Player p) {
        boolean attached = false;
        var list = leashableInArea(level, blockPos, m -> m.getLeashHolder() == p || m.getLeashHolder() == this);
        for (Leashable m : list) {
            if (m.getLeashHolder() == p) {
                m.setLeashedTo(this, true);
                attached = true;
            }
        }
        boolean dropped = false;
        if (!attached) {
            discard();
            if (p.hasInfiniteMaterials()) {
                for (Leashable m : list) {
                    if (m.isLeashed() && m.getLeashHolder() == this) {
                        m.dropLeash(true, false);
                        dropped = true;
                    }
                }
            }
        }
        if (attached || dropped) gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.BLOCK_ATTACH, p);
    }

    @Override protected void addAdditionalSaveData(NbtCompound tag) {}

    @Override protected void readAdditionalSaveData(NbtCompound tag) {}
}
