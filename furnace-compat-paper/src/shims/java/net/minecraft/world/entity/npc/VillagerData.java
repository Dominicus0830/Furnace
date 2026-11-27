package net.minecraft.world.entity.npc;

/**
 * 주민 데이터(Mojang 이름 shim, 엔티티 데이터 설계 D4): 종류·직업·레벨. 바닐라처럼 바꾸지 않는 값이고 setter 는 새 객체를 돌려준다.
 * 레벨은 1 아래면 1(바닐라 생성자 {@code Math.max(1, level)}). 코덱({@code CODEC}·{@code STREAM_CODEC})은 두지 않았다(부르면 없는 멤버).
 */
class VillagerData {

    public static final int MIN_VILLAGER_LEVEL = 1;
    public static final int MAX_VILLAGER_LEVEL = 5;
    /** 바닐라 NEXT_LEVEL_XP_THRESHOLDS: 레벨 1~5 의 시작 경험치. */
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    private final VillagerType type;
    private final VillagerProfession profession;
    private final int level;

    public VillagerData(VillagerType type, VillagerProfession profession, int level) {
        this.type = type;
        this.profession = profession;
        this.level = Math.max(1, level);
    }

    public VillagerType getType() { return type; }

    public VillagerProfession getProfession() { return profession; }

    public int getLevel() { return level; }

    public VillagerData setType(VillagerType type) { return new VillagerData(type, profession, level); }

    public VillagerData setProfession(VillagerProfession profession) { return new VillagerData(type, profession, level); }

    public VillagerData setLevel(int level) { return new VillagerData(type, profession, level); }

    /** 바닐라: 그 레벨의 시작 경험치, 레벨업할 수 없으면 0. */
    public static int getMinXpPerLevel(int level) { return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level - 1] : 0; }

    /** 바닐라: 그 레벨의 끝 경험치(다음 레벨 시작), 레벨업할 수 없으면 0. */
    public static int getMaxXpPerLevel(int level) { return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level] : 0; }

    public static boolean canLevelUp(int level) { return level >= MIN_VILLAGER_LEVEL && level < MAX_VILLAGER_LEVEL; }
}
