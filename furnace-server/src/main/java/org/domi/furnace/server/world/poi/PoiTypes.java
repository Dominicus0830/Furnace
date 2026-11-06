// 바닐라 PoiTypes(1.21.1): 등록 순서·티켓 수·유효 거리·블록 상태 대응을 그대로 옮긴다.
// 블록 상태 대응은 블록 레지스트리에서 이름으로 만든다(침대는 minecraft:beds 태그의 head 칸만).
// 태그 point_of_interest_type: acquirable_job_site(직업 13종), village(직업 + home + meeting), bee_home(beehive, bee_nest).
package org.domi.furnace.server.world.poi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Prop;

public final class PoiTypes {
    private static final List<PoiType> ALL = new ArrayList<>();
    private static final Map<String, PoiType> BY_NAME = new java.util.HashMap<>();

    public static final PoiType ARMORER = reg("armorer", 1, 1);
    public static final PoiType BUTCHER = reg("butcher", 1, 1);
    public static final PoiType CARTOGRAPHER = reg("cartographer", 1, 1);
    public static final PoiType CLERIC = reg("cleric", 1, 1);
    public static final PoiType FARMER = reg("farmer", 1, 1);
    public static final PoiType FISHERMAN = reg("fisherman", 1, 1);
    public static final PoiType FLETCHER = reg("fletcher", 1, 1);
    public static final PoiType LEATHERWORKER = reg("leatherworker", 1, 1);
    public static final PoiType LIBRARIAN = reg("librarian", 1, 1);
    public static final PoiType MASON = reg("mason", 1, 1);
    public static final PoiType SHEPHERD = reg("shepherd", 1, 1);
    public static final PoiType TOOLSMITH = reg("toolsmith", 1, 1);
    public static final PoiType WEAPONSMITH = reg("weaponsmith", 1, 1);
    public static final PoiType HOME = reg("home", 1, 1);
    public static final PoiType MEETING = reg("meeting", 32, 6);
    public static final PoiType BEEHIVE = reg("beehive", 0, 1);
    public static final PoiType BEE_NEST = reg("bee_nest", 0, 1);
    public static final PoiType NETHER_PORTAL = reg("nether_portal", 0, 1);
    public static final PoiType LODESTONE = reg("lodestone", 0, 1);
    public static final PoiType LIGHTNING_ROD = reg("lightning_rod", 0, 1);

    /** 태그 #minecraft:acquirable_job_site. */
    public static final Predicate<PoiType> ACQUIRABLE_JOB_SITE = t -> t.index() <= WEAPONSMITH.index();
    /** 태그 #minecraft:village. */
    public static final Predicate<PoiType> VILLAGE = t -> t.index() <= MEETING.index();
    /** 태그 #minecraft:bee_home. */
    public static final Predicate<PoiType> BEE_HOME = t -> t == BEEHIVE || t == BEE_NEST;

    private static PoiType reg(String name, int maxTickets, int validRange) {
        PoiType t = new PoiType("minecraft:" + name, ALL.size(), maxTickets, validRange);
        ALL.add(t);
        BY_NAME.put(t.name(), t);
        return t;
    }

    public static List<PoiType> all() { return Collections.unmodifiableList(ALL); }

    public static PoiType byName(String name) { return BY_NAME.get(name.indexOf(':') < 0 ? "minecraft:" + name : name); }

    // ------------------------------------------------------------------ 블록 상태 → 종류(TYPE_BY_STATE)

    private static volatile PoiType[] byState;

    private static PoiType[] table() {
        PoiType[] t = byState;
        if (t != null) return t;
        synchronized (PoiTypes.class) {
            if (byState != null) return byState;
            Blocks bl = B.BLOCKS;
            PoiType[] arr = new PoiType[bl.stateCount()];
            allStates(arr, bl, "blast_furnace", ARMORER);
            allStates(arr, bl, "smoker", BUTCHER);
            allStates(arr, bl, "cartography_table", CARTOGRAPHER);
            allStates(arr, bl, "brewing_stand", CLERIC);
            allStates(arr, bl, "composter", FARMER);
            allStates(arr, bl, "barrel", FISHERMAN);
            allStates(arr, bl, "fletching_table", FLETCHER);
            for (String c : new String[] {"cauldron", "lava_cauldron", "water_cauldron", "powder_snow_cauldron"}) allStates(arr, bl, c, LEATHERWORKER);
            allStates(arr, bl, "lectern", LIBRARIAN);
            allStates(arr, bl, "stonecutter", MASON);
            allStates(arr, bl, "loom", SHEPHERD);
            allStates(arr, bl, "smithing_table", TOOLSMITH);
            allStates(arr, bl, "grindstone", WEAPONSMITH);
            Prop part = B.prop("part");
            for (String n : org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf("minecraft:beds")) {
                int b = bl.block(n);
                if (b < 0) continue;
                for (int s = bl.firstState(b), e = s + bl.stateCountOf(b); s < e; s++) {
                    if ("head".equals(part.valueName(s))) arr[s] = HOME;
                }
            }
            allStates(arr, bl, "bell", MEETING);
            allStates(arr, bl, "beehive", BEEHIVE);
            allStates(arr, bl, "bee_nest", BEE_NEST);
            allStates(arr, bl, "nether_portal", NETHER_PORTAL);
            allStates(arr, bl, "lodestone", LODESTONE);
            allStates(arr, bl, "lightning_rod", LIGHTNING_ROD);
            byState = arr;
            return arr;
        }
    }

    private static void allStates(PoiType[] arr, Blocks bl, String name, PoiType type) {
        int b = bl.block("minecraft:" + name);
        if (b < 0) throw new IllegalStateException("POI 블록 없음: " + name);
        for (int s = bl.firstState(b), e = s + bl.stateCountOf(b); s < e; s++) {
            if (arr[s] != null) throw new IllegalStateException("POI 상태 중복: " + name);
            arr[s] = type;
        }
    }

    /** PoiTypes.forState: 그 상태의 POI 종류, 없으면 null. */
    public static PoiType forState(int state) {
        PoiType[] t = table();
        return state >= 0 && state < t.length ? t[state] : null;
    }

    /** PoiTypes.hasPoi. */
    public static boolean hasPoi(int state) { return forState(state) != null; }

    private PoiTypes() {}
}
