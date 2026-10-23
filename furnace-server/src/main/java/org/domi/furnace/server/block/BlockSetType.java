// 바닐라 BlockSetType: 문·다락문·압력판·버튼 묶음의 동작 플래그와 소리. 정의 인자 "block_set_type" 이름으로 찾는다.
package org.domi.furnace.server.block;

import java.util.HashMap;
import java.util.Map;

public record BlockSetType(String name, boolean canOpenByHand, boolean canOpenByWindCharge, boolean canButtonBeActivatedByArrows,
                           boolean plateSensitivityEverything, String doorClose, String doorOpen, String trapdoorClose,
                           String trapdoorOpen, String pressurePlateClickOff, String pressurePlateClickOn, String buttonClickOff,
                           String buttonClickOn) {

    private static final Map<String, BlockSetType> BY_NAME = new HashMap<>();

    private static BlockSetType reg(String name, boolean hand, boolean wind, boolean arrows, boolean everything, String... s) {
        BlockSetType t = new BlockSetType(name, hand, wind, arrows, everything, m(s[0]), m(s[1]), m(s[2]), m(s[3]), m(s[4]), m(s[5]), m(s[6]), m(s[7]));
        BY_NAME.put(name, t);
        return t;
    }

    private static String m(String s) { return "minecraft:" + s; }

    private static void wood(String name) {
        reg(name, true, true, true, true, "block.wooden_door.close", "block.wooden_door.open", "block.wooden_trapdoor.close",
                "block.wooden_trapdoor.open", "block.wooden_pressure_plate.click_off", "block.wooden_pressure_plate.click_on",
                "block.wooden_button.click_off", "block.wooden_button.click_on");
    }

    static {
        String[] metalPlate = {"block.metal_pressure_plate.click_off", "block.metal_pressure_plate.click_on"};
        reg("iron", false, false, false, true, "block.iron_door.close", "block.iron_door.open", "block.iron_trapdoor.close", "block.iron_trapdoor.open",
                metalPlate[0], metalPlate[1], "block.stone_button.click_off", "block.stone_button.click_on");
        reg("copper", true, true, false, true, "block.copper_door.close", "block.copper_door.open", "block.copper_trapdoor.close", "block.copper_trapdoor.open",
                metalPlate[0], metalPlate[1], "block.stone_button.click_off", "block.stone_button.click_on");
        reg("gold", false, true, false, true, "block.iron_door.close", "block.iron_door.open", "block.iron_trapdoor.close", "block.iron_trapdoor.open",
                metalPlate[0], metalPlate[1], "block.stone_button.click_off", "block.stone_button.click_on");
        for (String n : new String[] {"stone", "polished_blackstone"}) {
            reg(n, true, true, false, false, "block.iron_door.close", "block.iron_door.open", "block.iron_trapdoor.close", "block.iron_trapdoor.open",
                    "block.stone_pressure_plate.click_off", "block.stone_pressure_plate.click_on", "block.stone_button.click_off", "block.stone_button.click_on");
        }
        for (String n : new String[] {"oak", "spruce", "birch", "acacia", "jungle", "dark_oak", "mangrove"}) wood(n);
        reg("cherry", true, true, true, true, "block.cherry_wood_door.close", "block.cherry_wood_door.open", "block.cherry_wood_trapdoor.close",
                "block.cherry_wood_trapdoor.open", "block.cherry_wood_pressure_plate.click_off", "block.cherry_wood_pressure_plate.click_on",
                "block.cherry_wood_button.click_off", "block.cherry_wood_button.click_on");
        for (String n : new String[] {"crimson", "warped"}) {
            reg(n, true, true, true, true, "block.nether_wood_door.close", "block.nether_wood_door.open", "block.nether_wood_trapdoor.close",
                    "block.nether_wood_trapdoor.open", "block.nether_wood_pressure_plate.click_off", "block.nether_wood_pressure_plate.click_on",
                    "block.nether_wood_button.click_off", "block.nether_wood_button.click_on");
        }
        reg("bamboo", true, true, true, true, "block.bamboo_wood_door.close", "block.bamboo_wood_door.open", "block.bamboo_wood_trapdoor.close",
                "block.bamboo_wood_trapdoor.open", "block.bamboo_wood_pressure_plate.click_off", "block.bamboo_wood_pressure_plate.click_on",
                "block.bamboo_wood_button.click_off", "block.bamboo_wood_button.click_on");
    }

    public static BlockSetType byName(String name) {
        BlockSetType t = BY_NAME.get(name);
        if (t == null) throw new IllegalArgumentException("알 수 없는 block_set_type: " + name);
        return t;
    }

    /** 블록 정의 인자의 block_set_type. */
    public static BlockSetType of(int block) { return byName(Blocks.get().params(block).get("block_set_type").getAsString()); }

    /** 바닐라 WoodType 의 울타리 문 소리(닫힘, 열림). 정의 인자 "wood_type". */
    public static String[] fenceGateSounds(int block) {
        String w = Blocks.get().params(block).get("wood_type").getAsString();
        return switch (w) {
            case "cherry" -> new String[] {m("block.cherry_wood_fence_gate.close"), m("block.cherry_wood_fence_gate.open")};
            case "crimson", "warped" -> new String[] {m("block.nether_wood_fence_gate.close"), m("block.nether_wood_fence_gate.open")};
            case "bamboo" -> new String[] {m("block.bamboo_wood_fence_gate.close"), m("block.bamboo_wood_fence_gate.open")};
            default -> new String[] {m("block.fence_gate.close"), m("block.fence_gate.open")};
        };
    }
}
