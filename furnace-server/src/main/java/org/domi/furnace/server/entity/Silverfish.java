// 바닐라 Silverfish: 맞으면 20틱 뒤 근처(±10, ±5)의 숨은 블록에서 친구를 깨우고, 가끔 블록 속으로 들어가 숨는다. 체력 8, 공격력 1.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.InfestedBlock;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.*;

public class Silverfish extends Monster {
    private SilverfishWakeUpFriendsGoal friendsGoal;

    public Silverfish(ChunkLevel level, int id) { super(EntityType.SILVERFISH, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 8.0).add(Attribute.MOVEMENT_SPEED, 0.25).add(Attribute.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        friendsGoal = new SilverfishWakeUpFriendsGoal(this);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this));   // 바닐라 ckk.B
        goalSelector.addGoal(3, friendsGoal);
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new SilverfishMergeWithStoneGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if ((attacker != null || source.type().equals("magic")) && friendsGoal != null) friendsGoal.notifyHurt();
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public void tick() {
        yBodyRot = yRot();
        super.tick();
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        return InfestedBlock.isCompatibleHostBlock(level.getBlockState(pos.below())) ? 10.0F : super.getWalkTargetValue(pos, level);
    }

    @Override public String getAmbientSound() { return "entity.silverfish.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.silverfish.hurt"; }

    @Override protected String getDeathSound() { return "entity.silverfish.death"; }

    // ------------------------------------------------------------------ 목표

    static final class SilverfishWakeUpFriendsGoal extends Goal {
        private final Silverfish silverfish;
        private int lookForFriends;

        SilverfishWakeUpFriendsGoal(Silverfish s) { this.silverfish = s; }

        void notifyHurt() {
            if (lookForFriends == 0) lookForFriends = adjustedTickDelay(20);
        }

        @Override public boolean canUse() { return lookForFriends > 0; }

        @Override
        public void tick() {
            lookForFriends--;
            if (lookForFriends <= 0) {
                var level = silverfish.level();
                var r = silverfish.random();
                Pos base = silverfish.blockPosition();
                for (int i = 0; i <= 5 && i >= -5; i = (i <= 0 ? 1 : 0) - i) {
                    for (int j = 0; j <= 10 && j >= -10; j = (j <= 0 ? 1 : 0) - j) {
                        for (int k = 0; k <= 10 && k >= -10; k = (k <= 0 ? 1 : 0) - k) {
                            Pos p = new Pos(base.x() + j, base.y() + i, base.z() + k);
                            int state = level.getBlockState(p);
                            if (org.domi.furnace.server.block.BlockBehaviors.of(state) instanceof InfestedBlock ib) {
                                boolean griefing = level.gameRule("mobGriefing");
                                // CraftBukkit(+Paper 새 상태): EntityChangeBlockEvent 를 막으면 이 칸은 건너뛴다(난수 return 검사도 하지 않는다)
                                if (!org.domi.furnace.server.block.B.entityChange(level, silverfish, p,
                                        griefing ? org.domi.furnace.server.block.B.fluidLeftover(state) : ib.hostStateByInfested(state))) continue;
                                if (griefing) level.destroyBlock(p, true, null, 512);
                                else level.setBlock(p, ib.hostStateByInfested(state), 3, 512);
                                if (r.nextBoolean()) return;
                            }
                        }
                    }
                }
            }
        }
    }

    static final class SilverfishMergeWithStoneGoal extends RandomStrollGoal {
        private int selectedDirection = -1;
        private boolean doMerge;
        private final Silverfish silverfish;

        SilverfishMergeWithStoneGoal(Silverfish s) {
            super(s, 1.0, 10);
            this.silverfish = s;
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (silverfish.getTarget() != null) return false;
            if (!silverfish.getNavigation().isDone()) return false;
            var r = silverfish.random();
            if (silverfish.level().gameRule("mobGriefing") && r.nextInt(reducedTickDelay(10)) == 0) {
                selectedDirection = Dir.getRandom(r);
                Pos p = new Pos((int) Math.floor(silverfish.x()), (int) Math.floor(silverfish.y() + 0.5), (int) Math.floor(silverfish.z())).relative(selectedDirection);
                int state = silverfish.level().getBlockState(p);
                if (InfestedBlock.isCompatibleHostBlock(state)) {
                    doMerge = true;
                    return true;
                }
            }
            doMerge = false;
            return super.canUse();
        }

        @Override public boolean canContinueToUse() { return doMerge ? false : super.canContinueToUse(); }

        @Override
        public void start() {
            if (!doMerge) {
                super.start();
            } else {
                var level = silverfish.level();
                Pos p = new Pos((int) Math.floor(silverfish.x()), (int) Math.floor(silverfish.y() + 0.5), (int) Math.floor(silverfish.z())).relative(selectedDirection);
                int state = level.getBlockState(p);
                if (InfestedBlock.isCompatibleHostBlock(state)) {
                    // CraftBukkit: EntityChangeBlockEvent 를 막으면 숨지 않는다
                    if (!org.domi.furnace.server.block.B.entityChange(level, silverfish, p, InfestedBlock.infestedStateByHost(state))) return;
                    level.setBlock(p, InfestedBlock.infestedStateByHost(state), 3, 512);
                    silverfish.spawnAnim();
                    silverfish.discard();
                }
            }
        }
    }

    /** Silverfish.playStepSound: silverfish.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.silverfish.step", 0.15F, 1.0F); }

    /** getMovementEmission = EVENTS: 게임 이벤트만 내고 걸음·헤엄 소리는 이동으로 내지 않는다. */
    @Override
    protected MovementEmission getMovementEmission() { return MovementEmission.EVENTS; }
}
