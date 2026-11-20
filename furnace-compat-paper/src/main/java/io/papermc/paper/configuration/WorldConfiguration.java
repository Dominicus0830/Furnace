package io.papermc.paper.configuration;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Paper 월드별 설정({@code ServerLevel#paperConfig()}, NMS shim 5e — voxy 가 엑스레이 막기 설정을 읽어 먼 지형에서도 광석을 가린다). Furnace 는 Paper 의
 * 엑스레이 막기가 없으므로(paper-world 설정 가져오기도 "지원 안 함" 으로 알린다) {@code anticheat.antiXray.enabled} 는 늘 거짓이고 나머지는 Paper 기본값이다.
 * <p>지금은 {@code anticheat.antiXray} 부분만 있다 — 다른 묶음(entities·chunks 등)을 읽는 플러그인은 Paper 와 달리 그 필드가 없다(NoSuchFieldError).
 */
public class WorldConfiguration extends ConfigurationPart {

    static final int CURRENT_VERSION = 31;

    public int version = CURRENT_VERSION;
    public Anticheat anticheat = new Anticheat();

    WorldConfiguration() {}

    /** Paper: 기본값 묶음(월드 기본 설정 파일)인가. Furnace 의 월드별 값은 늘 그 월드의 것이다. */
    public boolean isDefault() { return false; }

    /** 서버의 월드 하나에 붙는 값(Furnace — 서버마다 같은 기본값). */
    public static WorldConfiguration furnaceDefaults() { return new WorldConfiguration(); }

    public class Anticheat extends ConfigurationPart {
        public AntiXray antiXray = new AntiXray();

        public Anticheat() {}

        public class AntiXray extends ConfigurationPart {
            public boolean enabled = false;
            public io.papermc.paper.configuration.type.EngineMode engineMode = io.papermc.paper.configuration.type.EngineMode.HIDE;
            public int maxBlockHeight = 64;
            public int updateRadius = 2;
            public boolean lavaObscures = false;
            public boolean usePermission = false;
            public List<Block> hiddenBlocks = List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.GOLD_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                    Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.MOSSY_COBBLESTONE, Blocks.OBSIDIAN, Blocks.CHEST, Blocks.DIAMOND_ORE,
                    Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.CLAY, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                    Blocks.ENDER_CHEST);
            public List<Block> replacementBlocks = List.of(Blocks.STONE, Blocks.OAK_PLANKS, Blocks.DEEPSLATE);

            public AntiXray() {}
        }
    }
}
