// 바닐라 FallingBlockEntity: 떨어지는 모래·자갈·모루 등. 중력 0.04 로 떨어져 땅에 닿으면 그 칸에 다시 놓이고(놓을 수 없으면 아이템으로 떨어지고),
// 모루처럼 피해를 주는 종류는 내려앉는 거리에 비례해 아래 생물을 다치게 한다. 콘크리트 가루는 물에 닿으면 굳는다.
// TODO: 블록 엔티티 데이터(TileEntityData) 되살리기, 플레이어 건축 막기(blocksBuilding).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.types.AnvilBlock;
import org.domi.furnace.server.block.types.ConcretePowderBlock;
import org.domi.furnace.server.block.types.FallingBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class FallingBlockEntity extends Entity {
    public static final int DATA_START_POS = 8;
    private static final Prop WATERLOGGED = B.prop("waterlogged");
    private int blockState = B.defaultState("sand");
    public int time;
    public boolean dropItem = true;
    private boolean cancelDrop, hurtEntities;
    private int fallDamageMax = 40;
    private float fallDamagePerDistance;

    public FallingBlockEntity(ChunkLevel level, int id) { super(EntityType.FALLING_BLOCK, level, id); }

    private FallingBlockEntity(ChunkLevel level, int id, double x, double y, double z, int state) {
        this(level, id);
        this.blockState = WATERLOGGED.has(state) ? WATERLOGGED.with(state, false) : state;
        setPos(x, y, z);
        setDeltaMovement(0.0, 0.0, 0.0);
        xo = x;
        yo = y;
        zo = z;
        setStartPos(blockPosition());
    }

    /** FallingBlockEntity.fall(level, pos, state): 칸을 유체(없으면 공기)로 바꾸고 엔티티를 만든다. */
    public static FallingBlockEntity fall(ChunkLevel level, Pos pos, int state) {
        FallingBlockEntity e = new FallingBlockEntity(level, level.nextEntityId(), pos.x() + 0.5, pos.y(), pos.z() + 0.5, state);
        level.setBlock(pos, Fluids.createLegacyBlock(level.getFluidState(pos)), 3);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_START_POS, SetEntityData.SERIALIZER_BLOCK_POS, new int[] {0, 0, 0});
    }

    public void setStartPos(Pos p) { entityData.set(DATA_START_POS, new int[] {p.x(), p.y(), p.z()}); }

    public int blockState() { return blockState; }

    public void disableDrop() { cancelDrop = true; }

    public void setHurtsEntities(float perDistance, int max) {
        hurtEntities = true;
        fallDamagePerDistance = perDistance;
        fallDamageMax = max;
    }

    @Override public int spawnData() { return blockState; }

    @Override protected double defaultGravity() { return 0.04; }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void tick() {
        if (B.isAir(blockState)) {
            discard();
            return;
        }
        BlockBehavior behavior = BlockBehaviors.of(blockState);
        time++;
        applyGravity();
        move(MOVER_SELF, vx(), vy(), vz());
        handlePortal();
        if (!isRemoved() || forceTickAfterTeleportToDuplicate) {
            Pos pos = blockPosition();
            boolean concrete = behavior instanceof ConcretePowderBlock;
            boolean waterConcrete = concrete && Fluids.isWater(level.getFluidState(pos));
            double speed = vx() * vx() + vy() * vy() + vz() * vz();
            if (concrete && speed > 1.0) {
                var hit = Clip.clipCollider(level, xo, yo, zo, x(), y(), z(), Clip.FluidMode.SOURCE_ONLY);
                if (!hit.miss() && Fluids.isWater(level.getFluidState(hit.pos()))) {
                    pos = hit.pos();
                    waterConcrete = true;
                }
            }
            if (!onGround() && !waterConcrete) {
                if (time > 100 && (pos.y() <= level.minBuildHeight() || pos.y() > level.maxBuildHeight()) || time > 600) {
                    if (dropItem && level.gameRule("doEntityDrops")) spawnAtLocation(itemStack());
                    discard();
                }
            } else {
                int replaced = level.getBlockState(pos);
                setDeltaMovement(vx() * 0.7, vy() * -0.5, vz() * 0.7);
                if (!B.is(replaced, "moving_piston")) {
                    if (!cancelDrop) {
                        boolean canReplace = B.canBeReplaced(replaced, PlaceContext.directional(level, pos, Dir.DOWN, ItemStack.EMPTY, Dir.UP));
                        boolean hangs = FallingBlock.isFree(level.getBlockState(pos.below())) && (!concrete || !waterConcrete);
                        boolean canLand = B.canSurvive(blockState, level, pos) && !hangs;
                        if (canReplace && canLand) {
                            int place = blockState;
                            if (WATERLOGGED.has(place) && Fluids.isWater(level.getFluidState(pos))) place = WATERLOGGED.with(place, true);
                            if (level.setBlock(pos, place, 3)) {
                                discard();
                                behavior.onLand(level, pos, place, replaced, isSilent());
                            } else if (dropItem && level.gameRule("doEntityDrops")) {
                                discard();
                                behavior.onBrokenAfterFall(level, pos, isSilent());
                                spawnAtLocation(itemStack());
                            }
                        } else {
                            discard();
                            if (dropItem && level.gameRule("doEntityDrops")) {
                                behavior.onBrokenAfterFall(level, pos, isSilent());
                                spawnAtLocation(itemStack());
                            }
                        }
                    } else {
                        discard();
                        behavior.onBrokenAfterFall(level, pos, isSilent());
                    }
                }
            }
        }
        setDeltaMovement(vx() * 0.98, vy() * 0.98, vz() * 0.98);
    }

    /**
     * forceTickAfterTeleportToDuplicate: 엔드와 다른 차원 사이를 포털로 넘어간 직후에는, 이미 빠진 옛 엔티티가 이번 틱 끝까지 마저 돌아
     * 제자리에 블록을 놓는다(건너간 복사본과 함께 둘이 되는 바닐라 중력 블록 복제). 엔드가 끼지 않은 이동에는 켜지지 않는다.
     */
    private boolean forceTickAfterTeleportToDuplicate;

    @Override
    protected void afterChangedDimension(String from, String to) {
        boolean end = "minecraft:the_end".equals(from) || "minecraft:the_end".equals(to);
        forceTickAfterTeleportToDuplicate = end && to != null && !from.equals(to);
    }

    private static boolean isAnvil(int s) { return B.is(s, "anvil") || B.is(s, "chipped_anvil") || B.is(s, "damaged_anvil"); }

    private void spawnAtLocation(ItemStack stack) {
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);
    }

    private ItemStack itemStack() { return new ItemStack(BlockItems.asItemId(B.block(blockState)), 1); }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, Damage source) {
        if (!hurtEntities) return false;
        int i = (int) Math.ceil(distance - 1.0F);
        if (i < 0) return false;
        BlockBehavior behavior = BlockBehaviors.of(blockState);
        Damage src = new Damage(behavior.fallDamageType(), false, false, false);
        float amount = Math.min((float) Math.floor(i * fallDamagePerDistance), fallDamageMax);
        for (Entity e : level.getEntities(this, boundingBox(), en -> en.isAlive())) e.hurt(src, amount);
        java.util.List<Player> players = new java.util.ArrayList<>();
        level.forEachPlayerIn(boundingBox(), players::add);
        for (Player p : players) {
            if (!p.vitals().isSpectator() && !p.vitals().isCreative()) p.hurtByMob(src, amount, null);
        }
        if (isAnvil(blockState) && amount > 0.0F && random.nextFloat() < 0.05F + i * 0.05F) {
            int damaged = AnvilBlock.damage(blockState);
            if (damaged < 0) cancelDrop = true;
            else blockState = damaged;
        }
        return false;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putCompound("BlockState", org.domi.furnace.server.block.entity.PistonMovingBlockEntity.writeBlockState(blockState));
        tag.putInt("Time", time);
        tag.putBoolean("DropItem", dropItem);
        tag.putBoolean("HurtEntities", hurtEntities);
        tag.putFloat("FallHurtAmount", fallDamagePerDistance);
        tag.putInt("FallHurtMax", fallDamageMax);
        tag.putBoolean("CancelDrop", cancelDrop);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.tags().get("BlockState") instanceof NbtCompound c) blockState = org.domi.furnace.server.block.entity.PistonMovingBlockEntity.readBlockState(c);
        time = tag.getIntOr0("Time");
        if (tag.containsNumber("HurtEntities")) {
            hurtEntities = tag.getBoolean("HurtEntities");
            fallDamagePerDistance = tag.getFloat("FallHurtAmount");
            fallDamageMax = tag.getIntOr0("FallHurtMax");
        } else if (isAnvil(blockState)) {
            hurtEntities = true;
        }
        if (tag.containsNumber("DropItem")) dropItem = tag.getBoolean("DropItem");
        cancelDrop = tag.getBoolean("CancelDrop");
        if (B.isAir(blockState)) blockState = B.defaultState("sand");
    }
}
