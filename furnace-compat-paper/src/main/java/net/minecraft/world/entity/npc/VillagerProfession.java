package net.minecraft.world.entity.npc;

import com.google.common.collect.ImmutableSet;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.domi.furnace.compat.nms.NmsVillagers;

/**
 * 주민 직업(Mojang 이름 shim, 엔티티 데이터 설계 D4). 바닐라 record 와 같은 여섯 성분이고 값은 내장 레지스트리 {@code minecraft:villager_profession} 의 것이다.
 * 직업 블록·요청 아이템·보조 지점·일 소리는 바닐라 1.21.1 등록과 같다({@link NmsVillagers} — 서버 주민 정의와 시험으로 견준다).
 */
public record VillagerProfession(String name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite,
                                 ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, SoundEvent workSound) {

    /** 바닐라: 직업 블록 태그 #acquirable_job_site 에 드는 관심 지점. */
    public static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS = NmsVillagers.ALL_ACQUIRABLE_JOBS;

    public static final VillagerProfession NONE = NmsVillagers.registerProfession("none");
    public static final VillagerProfession ARMORER = NmsVillagers.registerProfession("armorer");
    public static final VillagerProfession BUTCHER = NmsVillagers.registerProfession("butcher");
    public static final VillagerProfession CARTOGRAPHER = NmsVillagers.registerProfession("cartographer");
    public static final VillagerProfession CLERIC = NmsVillagers.registerProfession("cleric");
    public static final VillagerProfession FARMER = NmsVillagers.registerProfession("farmer");
    public static final VillagerProfession FISHERMAN = NmsVillagers.registerProfession("fisherman");
    public static final VillagerProfession FLETCHER = NmsVillagers.registerProfession("fletcher");
    public static final VillagerProfession LEATHERWORKER = NmsVillagers.registerProfession("leatherworker");
    public static final VillagerProfession LIBRARIAN = NmsVillagers.registerProfession("librarian");
    public static final VillagerProfession MASON = NmsVillagers.registerProfession("mason");
    public static final VillagerProfession NITWIT = NmsVillagers.registerProfession("nitwit");
    public static final VillagerProfession SHEPHERD = NmsVillagers.registerProfession("shepherd");
    public static final VillagerProfession TOOLSMITH = NmsVillagers.registerProfession("toolsmith");
    public static final VillagerProfession WEAPONSMITH = NmsVillagers.registerProfession("weaponsmith");

    @Override
    public String toString() { return name; }
}
