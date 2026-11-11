// A12-06: 아홀로틀이 주는 재생 효과가 effects_changed 의 source 로 넘어가 "친구와 함께해요"(husbandry/kill_axolotl_target) 조건이 맞는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.advancement.AdvancementManager;
import org.domi.furnace.server.advancement.PlayerAdvancements;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.loot.*;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A12EffectSourceTest {

    @Test
    void sourceOfAddedEffectReachesEffectsChangedTrigger(@TempDir Path dir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(TestRecipes.manager() != null);
        Path adv = dir.resolve("pack/data/test/advancement");
        Files.createDirectories(adv);
        Files.writeString(adv.resolve("friend.json"), "{\"criteria\":{\"c\":{\"trigger\":\"minecraft:effects_changed\","
                + "\"conditions\":{\"source\":{\"type\":\"minecraft:axolotl\"}}}},\"requirements\":[[\"c\"]],\"rewards\":{\"experience\":5}}");
        PackRepository repo = new PackRepository(dir.resolve("pack"), null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        LootRegistries regs;
        LootData loot;
        AdvancementManager manager;
        try (ResourceManager rm = repo.openResourceManager()) {
            regs = new LootRegistries(tags, tags.loadAll(rm));
            loot = LootData.load(rm, regs);
            manager = AdvancementManager.load(rm, regs, new NetworkComponents(tags));
        }
        NoiseRandom random = new XoroshiroRandomSource(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { return random; }
            public NoiseRandom randomSequence(ResourceId id) { return random; }
        };
        int[] xp = {0};
        PlayerAdvancements.Host host = new PlayerAdvancements.Host() {
            public void send(Packet p) {}
            public void awardRecipes(List<ResourceId> r) {}
            public void giveExperience(int points) { xp[0] += points; }
            public void giveLoot(String table) {}
            public void broadcast(Text message) {}
            public boolean announceAdvancements() { return false; }
            public Text displayName() { return Text.text("P"); }
            public LootContext playerContext() {
                return new LootContext(loot, world, new LootParams().with(LootParams.ORIGIN, new double[] {0, 64, 0})
                        .with(LootParams.THIS_ENTITY, (LootEntity) () -> "minecraft:player"), random);
            }
            public LootRegistries registries() { return regs; }
        };
        Player p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        PlayerAdvancements a = new PlayerAdvancements(dir, p.uuid(), manager, host);
        p.advancements(a);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);

        // 주인이 없는 효과(예: 물약)는 source 가 없어 맞지 않는다
        p.vitals().addEffect(new MobEffectInstance(MobEffect.REGENERATION, 100, 0));
        p.drainOwnerTasks();
        assertEquals(0, xp[0]);
        // 아홀로틀이 준 효과 — source = 아홀로틀
        p.vitals().addEffect(new MobEffectInstance(MobEffect.REGENERATION, 200, 0), () -> "minecraft:axolotl");
        p.drainOwnerTasks();
        assertEquals(5, xp[0]);

        // 아홀로틀 경로(Axolotl.rewardPlayer)로도 source 가 넘어간다
        a.revoke(manager.get(ResourceId.parse("test:friend")), "c");
        xp[0] = 0;
        p.vitals().removeAllEffects();
        p.drainOwnerTasks();
        Axolotl.rewardPlayer(p, new Axolotl(null, 1));
        p.drainOwnerTasks();
        assertEquals(5, xp[0], "Axolotl.rewardPlayer 가 효과를 줄 때 source 를 넘기지 않으면 안 맞는다");
    }
}
