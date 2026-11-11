// A06-01: 구조물 조각을 바닐라 StructurePiece.save 형식으로 쓴다 — 바닐라 PiecesContainer.load 가 조각 종류로 찾고
// 각 조각 생성자(ub)가 읽는 키가 모두 있어야 한다(없으면 바닐라·Paper 로 옮길 때 시작점이 빈 것이 된다).
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class StructurePieceSaveTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGS = RegistrySet.load("registries/1.21.1.txt");

    /** 바닐라 조각 종류별로 load 생성자가 읽는 키(공통 id·BB·O·GD 제외). eki·각 조각 클래스 디컴파일 대조. */
    private static final Map<String, List<String>> KEYS = new HashMap<>();
    private static void keys(String types, String... k) { for (String t : types.split(",")) KEYS.put(t, List.of(k)); }
    static {
        keys("mscorridor", "MST", "hr", "sc", "hps", "Num");
        keys("mscrossing", "MST", "tf", "D");
        keys("msroom", "MST", "Entrances");
        keys("msstairs", "MST");
        keys("nebcr,nebs,neccs,nectb,nece,nescsc,nesc,necsr,nerc,nesr,nestart");
        keys("nebef", "Seed");
        keys("nesclt,nescrt", "Chest");
        keys("nemt", "Mob");
        keys("shlt,shrt,shph,shssd", "EntryDoor");
        keys("shcc", "EntryDoor", "Chest");
        keys("shfc", "EntryDoor", "Steps");
        keys("sh5c", "EntryDoor", "leftLow", "leftHigh", "rightLow", "rightHigh");
        keys("shli", "EntryDoor", "Tall");
        keys("shpr", "EntryDoor", "Mob");
        keys("shrc", "EntryDoor", "Type");
        keys("shsd,shstart", "EntryDoor", "Source");
        keys("shs", "EntryDoor", "Left", "Right");
        keys("tejp", "Width", "Height", "Depth", "HPos", "placedMainChest", "placedHiddenChest", "placedTrap1", "placedTrap2");
        keys("tesh", "Width", "Height", "Depth", "HPos", "Witch", "Cat");
        keys("tedp", "Width", "Height", "Depth", "HPos", "hasPlacedChest0", "hasPlacedChest1", "hasPlacedChest2", "hasPlacedChest3");
        keys("omb,omcr,omdxr,omdxyr,omdyr,omdyzr,omdzr,omentry,ompenthouse,omsimple,omsimplet,omwr");
        keys("btp");
        keys("iglu,nefos", "TPX", "TPY", "TPZ", "Template", "Rot");
        keys("orp", "TPX", "TPY", "TPZ", "Template", "Rot", "Integrity", "BiomeType", "IsLarge");
        keys("rupo", "TPX", "TPY", "TPZ", "Template", "Rotation", "Mirror", "VerticalPlacement", "Properties");
        keys("ecp", "TPX", "TPY", "TPZ", "Template", "Rot", "OW");
        keys("wmp", "TPX", "TPY", "TPZ", "Template", "Rot", "Mi");
        keys("shipwreck", "TPX", "TPY", "TPZ", "Template", "isBeached", "Rot");
        keys("jigsaw", "PosX", "PosY", "PosZ", "ground_level_delta", "pool_element", "rotation", "junctions");
    }
    private static final Set<String> ROTATIONS = Set.of("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90");
    private static final Set<String> ELEMENT_TYPES = Set.of("minecraft:single_pool_element", "minecraft:legacy_single_pool_element",
            "minecraft:feature_pool_element", "minecraft:list_pool_element", "minecraft:empty_pool_element");

    private static String dimensionOf(String id) {
        return switch (id) {
            case "minecraft:fortress", "minecraft:bastion_remnant", "minecraft:nether_fossil", "minecraft:ruined_portal_nether" -> "nether";
            case "minecraft:end_city" -> "end";
            default -> "overworld";
        };
    }

    /** 모든 구조물을 명령 경로(/place structure)로 몇 군데 만들어 조각마다 저장 형식을 본다. */
    @Test
    void everyPieceSavesInVanillaFormat() {
        Map<String, NoiseWorldSource> gens = Map.of(
                "overworld", NoiseWorldSource.overworld(1234L, REG, REGS),
                "nether", NoiseWorldSource.nether(1234L, REG, REGS),
                "end", NoiseWorldSource.end(1234L, REG, REGS));
        Map<String, Integer> seen = new TreeMap<>();
        for (String id : new java.util.TreeSet<>(StructureRegistry.structureJsons().keySet())) {
            NoiseWorldSource gen = gens.get(dimensionOf(id));
            int found = 0;
            for (int i = 0; i < 40 && found < 2; i++) {
                int cx = id.equals("minecraft:end_city") ? 60 + i * 7 : i * 11 - 200, cz = id.equals("minecraft:end_city") ? 40 : i * 5 - 100;
                StructureEngine.CommandStart cs = gen.structureEngine().generateForCommand(id, cx, cz, gen);
                if (!cs.supported() || cs.start() == null || cs.start().pieces().isEmpty()) continue;
                found++;
                for (StructurePiece p : cs.start().pieces()) check(id, p.save(), seen);
            }
        }
        // 바닐라 조각 종류 대부분이 실제로 나와야 시험이 의미 있다
        assertTrue(seen.size() >= 40, "나온 조각 종류 " + seen.size() + ": " + seen.keySet());
    }

    private static void check(String structure, NbtCompound t, Map<String, Integer> seen) {
        String id = t.getString("id");
        assertTrue(id.startsWith("minecraft:"), structure + " " + id);
        String type = id.substring("minecraft:".length());
        List<String> want = KEYS.get(type);
        assertNotNull(want, structure + ": 바닐라 StructurePieceType 에 없는 조각 " + id);
        seen.merge(type, 1, Integer::sum);
        assertTrue(t.tags().get("BB") instanceof int[] bb && bb.length == 6 && bb[0] <= bb[3] && bb[1] <= bb[4] && bb[2] <= bb[5], structure + " BB");
        assertTrue(t.tags().get("O") instanceof Integer o && o >= -1 && o <= 3, structure + " O");
        assertTrue(t.tags().get("GD") instanceof Integer, structure + " GD");
        for (String k : want) assertTrue(t.tags().containsKey(k), structure + " " + type + " 에 " + k + " 없음: " + t.tags().keySet());
        for (String k : List.of("Rot", "rotation", "Rotation")) {
            if (t.tags().get(k) instanceof String r) assertTrue(ROTATIONS.contains(r), structure + " " + k + "=" + r);
        }
        if (type.equals("jigsaw")) {
            assertEquals(-1, t.getInt("O"), "PoolElementStructurePiece 는 방향이 없다");
            checkElement(structure, t.getCompound("pool_element"));
            for (Object o : t.getList("junctions").items()) {
                NbtCompound j = (NbtCompound) o;
                for (String k : List.of("source_x", "source_ground_y", "source_z", "delta_y", "dest_proj")) assertTrue(j.tags().containsKey(k));
            }
        }
        if (KEYS.get(type).contains("Template")) {
            assertTrue(t.getString("Template").contains(":"), structure + " Template " + t.getString("Template"));
            assertEquals(2, t.getInt("O"), "TemplateStructurePiece 는 NORTH");
        }
    }

    private static void checkElement(String structure, NbtCompound e) {
        String type = e.getString("element_type");
        assertTrue(ELEMENT_TYPES.contains(type), structure + " element_type " + type);
        switch (type) {
            case "minecraft:single_pool_element", "minecraft:legacy_single_pool_element" -> {
                assertTrue(e.getString("location").contains(":"));
                assertTrue(e.tags().get("processors") instanceof String || e.tags().get("processors") instanceof NbtCompound);
                assertTrue(Set.of("rigid", "terrain_matching").contains(e.getString("projection")));
            }
            case "minecraft:feature_pool_element" -> assertTrue(e.getString("feature").contains(":"));
            case "minecraft:list_pool_element" -> {
                NbtList l = e.getList("elements");
                assertTrue(l.size() > 0);
                for (Object o : l.items()) {
                    checkElement(structure, (NbtCompound) o);
                    assertEquals(e.getString("projection"), ((NbtCompound) o).getString("projection"), "목록 안 요소의 projection 은 목록의 것");
                }
            }
            default -> {}
        }
    }

    /** 청크 저장: 시작점의 Children 이 조각 save 결과이고, Furnace 가 다시 읽으면 조각 상자가 같다. */
    @Test
    void chunkStructuresRoundTrip() {
        NoiseWorldSource gen = NoiseWorldSource.overworld(1234L, REG, REGS);
        StructureEngine.CommandStart cs = null;
        for (int i = 0; i < 40 && (cs == null || cs.start() == null); i++) cs = gen.structureEngine().generateForCommand("minecraft:village_plains", i * 9, 3, gen);
        assertNotNull(cs.start());
        StructureStart s = cs.start();
        NbtCompound nbt = ChunkStructures.generated(s.chunkX(), s.chunkZ(), List.of(s), List.of()).toNbt();
        NbtCompound st = nbt.getCompound("starts").getCompound("minecraft:village_plains");
        assertEquals("minecraft:village_plains", st.getString("id"));
        assertEquals(s.pieces().size(), st.getList("Children").size());
        assertEquals("minecraft:jigsaw", ((NbtCompound) st.getList("Children").items().get(0)).getString("id"));
        ChunkStructures back = ChunkStructures.fromNbt(nbt);
        assertArrayEquals(ChunkStructures.refOf(s).pieces(), back.ownStarts()[0].pieces());
    }
}
