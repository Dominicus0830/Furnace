// D2 시험용 플러그인: 부트스트랩에서 게임 레지스트리 항목·태그를 더하고, 켜질 때 게임 규칙·마법부여 끄기·난이도 프로필을 건다.
package org.domi.furnace.server.plugin;

import java.util.List;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.registry.EnchantmentDefinition;
import org.domi.furnace.api.registry.PaintingVariant;
import org.domi.furnace.api.registry.WolfVariant;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Difficulty;
import org.domi.furnace.api.world.DifficultyProfile;
import org.domi.furnace.api.world.GameRule;

public final class TestGameBoots {

    private TestGameBoots() {}

    public static volatile RuntimeException badKey;
    public static volatile GameRule<Boolean> pvp;
    public static volatile GameRule<Integer> lives;

    public static final class GameBoot implements PluginBootstrap {
        @Override
        public void bootstrap(BootstrapContext ctx) {
            ctx.lifecycle().register(LifecycleEvents.GAME_REGISTRIES, reg -> {
                reg.wolfVariant("game:moon", new WolfVariant("game:entity/wolf/moon", "game:entity/wolf/moon_tame",
                        "game:entity/wolf/moon_angry", List.of("minecraft:plains")));
                reg.paintingVariant("game:big", new PaintingVariant(4, 3, "game:big"));
                reg.enchantment("game:zap", EnchantmentDefinition.builder(Text.text("Zap"))
                        .supportedItems("#minecraft:enchantable/sword").weight(5).maxLevel(3)
                        .minCost(5, 8).maxCost(55, 8).anvilCost(2).slots("mainhand").build());
                reg.json("minecraft:banner_pattern", "game:stripe", "{\"asset_id\":\"game:stripe\",\"translation_key\":\"block.game.stripe\"}");
                reg.addToTag("minecraft:enchantment", "minecraft:in_enchanting_table", "game:zap");
                reg.addToTag("minecraft:painting_variant", "minecraft:placeable", "game:big");
                reg.registerBiome("glow", org.domi.furnace.api.worldgen.BiomeDefinition.builder().temperature(0.1f).build());   // 같은 훅의 W2 몫
                try {
                    reg.wolfVariant("other:wolf", new WolfVariant("a:b", "a:b", "a:b", List.of()));
                } catch (RuntimeException e) {
                    badKey = e;
                }
            });
        }
    }

    public static final class Game extends FurnacePlugin {
        @Override
        public void onEnable() {
            Furnace f = server();
            pvp = f.gameRules().registerBoolean(this, "pvpArena", true);
            lives = f.gameRules().registerInt(this, "game:lives", 3, 1, 10);
            f.gameRegistries().disableEnchantment(this, "minecraft:mending");
            f.difficulty().setProfile(this, new DifficultyProfile("game:brutal", Difficulty.HARD, 2.0F, 1.5F));
        }
    }
}
