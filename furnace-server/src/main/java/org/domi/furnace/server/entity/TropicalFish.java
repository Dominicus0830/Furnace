// 바닐라 TropicalFish: 무늬(종류 12가지)와 두 색으로 이뤄진 변종 정수 하나를 가진다. 스폰할 때 90% 는 미리 정해진 22가지 중 하나, 10% 는 무작위 조합.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class TropicalFish extends AbstractSchoolingFish {
    public static final int DATA_ID_TYPE_VARIANT = 17;

    /** 무늬 번호(큰 무늬 비트 | 번호 << 8): kob, sunstreak, snooper, dasher, brinely, spotty, flopper, stripey, glitter, blockfish, betty, clayfish. */
    private static final int[] PATTERNS = {0, 256, 512, 768, 1024, 1280, 1, 257, 513, 769, 1025, 1281};
    private static final int STRIPEY = 257, FLOPPER = 1, CLAYFISH = 1281, SUNSTREAK = 256, KOB = 0, SPOTTY = 1280, BLOCKFISH = 769, GLITTER = 513,
            DASHER = 768, BRINELY = 1024, BETTY = 1025, SNOOPER = 512;
    // DyeColor 번호
    private static final int WHITE = 0, ORANGE = 1, LIGHT_BLUE = 3, YELLOW = 4, LIME = 5, PINK = 6, GRAY = 7, CYAN = 9, PURPLE = 10, BLUE = 11, RED = 14;

    /** 미리 정해진 22가지(무늬, 몸 색, 무늬 색). */
    private static final int[][] COMMON_VARIANTS = {
        {STRIPEY, ORANGE, GRAY}, {FLOPPER, GRAY, GRAY}, {FLOPPER, GRAY, BLUE}, {CLAYFISH, WHITE, GRAY}, {SUNSTREAK, BLUE, GRAY}, {KOB, ORANGE, WHITE},
        {SPOTTY, PINK, LIGHT_BLUE}, {BLOCKFISH, PURPLE, YELLOW}, {CLAYFISH, WHITE, RED}, {SPOTTY, WHITE, YELLOW}, {GLITTER, WHITE, GRAY},
        {CLAYFISH, WHITE, ORANGE}, {DASHER, CYAN, PINK}, {BRINELY, LIME, LIGHT_BLUE}, {BETTY, RED, WHITE}, {SNOOPER, GRAY, RED}, {BLOCKFISH, RED, WHITE},
        {FLOPPER, WHITE, YELLOW}, {KOB, RED, WHITE}, {SUNSTREAK, GRAY, WHITE}, {DASHER, CYAN, YELLOW}, {FLOPPER, YELLOW, YELLOW}};

    private boolean isSchool = true;

    public TropicalFish(ChunkLevel level, int id) { super(EntityType.TROPICAL_FISH, level, id); }

    private static int pack(int pattern, int color, int patternColor) { return pattern & 65535 | (color & 0xFF) << 16 | (patternColor & 0xFF) << 24; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_TYPE_VARIANT, SetEntityData.SERIALIZER_INT, 0);
    }

    public int getVariant() { return (Integer) entityData.get(DATA_ID_TYPE_VARIANT); }

    public void setVariant(int v) { entityData.set(DATA_ID_TYPE_VARIANT, v); }

    /** 무리 지어 스폰할 수 있나(무작위 변종이면 혼자). */
    public boolean isMaxGroupSizeReached(int size) { return !isSchool; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getIntOr0("Variant"));
    }

    @Override public ItemStack getBucketItemStack() { return stackOf("tropical_fish_bucket"); }

    /** TropicalFish.saveToBucketTag: 공통 데이터 + BucketVariantTag(변종 정수). */
    @Override
    public ItemStack saveToBucketTag(ItemStack bucket) {
        ItemStack s = Bucketable.saveDefaultDataToBucketTag(this, bucket);
        NbtCompound tag = Bucketable.bucketTag(s);
        tag.putInt("BucketVariantTag", getVariant());
        return Bucketable.withBucketTag(s, tag);
    }

    /** TropicalFish.loadFromBucketTag: 공통 데이터 + BucketVariantTag 가 정수(태그 3)일 때만 변종. */
    @Override
    public void loadFromBucketTag(NbtCompound tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        if (tag.tags().get("BucketVariantTag") instanceof Integer v) setVariant(v);
    }

    /** 처음 스폰된 변종을 무리가 함께 쓰는 그룹 데이터. */
    public static final class TropicalFishGroupData extends SchoolSpawnGroupData {
        final int variant;

        TropicalFishGroupData(TropicalFish leader, int variant) {
            super(leader);
            this.variant = variant;
        }
    }

    @Override
    public SchoolSpawnGroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, SchoolSpawnGroupData data) {
        data = super.finalizeSpawn(r, difficulty, data);
        int variant;
        if (data instanceof TropicalFishGroupData g) {
            variant = g.variant;
        } else if (r.nextFloat() < 0.9) {
            int[] v = COMMON_VARIANTS[r.nextInt(COMMON_VARIANTS.length)];
            variant = pack(v[0], v[1], v[2]);
            data = new TropicalFishGroupData(this, variant);
        } else {
            isSchool = false;
            int pattern = PATTERNS[r.nextInt(PATTERNS.length)];
            int c1 = r.nextInt(16);
            int c2 = r.nextInt(16);
            variant = pack(pattern, c1, c2);
        }
        setVariant(variant);
        return data;
    }

    @Override public String getAmbientSound() { return "entity.tropical_fish.ambient"; }

    @Override protected String getDeathSound() { return "entity.tropical_fish.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.tropical_fish.hurt"; }

    @Override protected String getFlopSound() { return "entity.tropical_fish.flop"; }
}
