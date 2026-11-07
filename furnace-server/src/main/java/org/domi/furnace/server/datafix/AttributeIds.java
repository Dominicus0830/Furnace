// 3945 AttributeIdFix 의 표: 옛 능력치 수정자 UUID·이름 → 1.21 수정자 id.
package org.domi.furnace.server.datafix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AttributeIds {
    private AttributeIds() {}

    static final Map<UUID, String> BY_UUID = new HashMap<>();
    static final Map<String, String> BY_NAME = Map.of(
        "Random spawn bonus", "minecraft:random_spawn_bonus",
        "Random zombie-spawn bonus", "minecraft:zombie_random_spawn_bonus",
        "Leader zombie bonus", "minecraft:leader_zombie_bonus",
        "Zombie reinforcement callee charge", "minecraft:reinforcement_callee_charge",
        "Zombie reinforcement caller charge", "minecraft:reinforcement_caller_charge");

    private static void put(String uuid, String id) { BY_UUID.put(UUID.fromString(uuid), id); }

    static {
        put("736565d2-e1a7-403d-a3f8-1aeb3e302542", "minecraft:creative_mode_block_range");
        put("98491ef6-97b1-4584-ae82-71a8cc85cf73", "minecraft:creative_mode_entity_range");
        put("91AEAA56-376B-4498-935B-2F7F68070635", "minecraft:effect.speed");
        put("7107DE5E-7CE8-4030-940E-514C1F160890", "minecraft:effect.slowness");
        put("AF8B6E3F-3328-4C0A-AA36-5BA2BB9DBEF3", "minecraft:effect.haste");
        put("55FCED67-E92A-486E-9800-B47F202C4386", "minecraft:effect.mining_fatigue");
        put("648D7064-6A60-4F59-8ABE-C2C23A6DD7A9", "minecraft:effect.strength");
        put("C0105BF3-AEF8-46B0-9EBC-92943757CCBE", "minecraft:effect.jump_boost");
        put("22653B89-116E-49DC-9B6B-9971489B5BE5", "minecraft:effect.weakness");
        put("5D6F0BA2-1186-46AC-B896-C61C5CEE99CC", "minecraft:effect.health_boost");
        put("EAE29CF0-701E-4ED6-883A-96F798F3DAB5", "minecraft:effect.absorption");
        put("03C3C89D-7037-4B42-869F-B146BCB64D2E", "minecraft:effect.luck");
        put("CC5AF142-2BD2-4215-B636-2605AED11727", "minecraft:effect.unluck");
        put("6555be74-63b3-41f1-a245-77833b3c2562", "minecraft:evil");
        put("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce", "minecraft:powder_snow");
        put("662A6B8D-DA3E-4C1C-8813-96EA6097278D", "minecraft:sprinting");
        put("020E0DFB-87AE-4653-9556-831010E291A0", "minecraft:attacking");
        put("766bfa64-11f3-11ea-8d71-362b9e155667", "minecraft:baby");
        put("7E0292F2-9434-48D5-A29F-9583AF7DF27F", "minecraft:covered");
        put("9e362924-01de-4ddd-a2b2-d0f7a405a174", "minecraft:suffocating");
        put("5CD17E52-A79A-43D3-A529-90FDE04B181E", "minecraft:drinking");
        put("B9766B59-9566-4402-BC1F-2EE2A276D836", "minecraft:baby");
        put("49455A49-7EC5-45BA-B886-3B90B23A1718", "minecraft:attacking");
        put("845DB27C-C624-495F-8C9F-6020A9A58B6B", "minecraft:armor.boots");
        put("D8499B04-0E66-4726-AB29-64469D734E0D", "minecraft:armor.leggings");
        put("9F3D476D-C118-4544-8365-64846904B48E", "minecraft:armor.chestplate");
        put("2AD3F246-FEE1-4E67-B886-69FD380BB150", "minecraft:armor.helmet");
        put("C1C72771-8B8E-BA4A-ACE0-81A93C8928B2", "minecraft:armor.body");
        put("b572ecd2-ac0c-4071-abde-9594af072a37", "minecraft:enchantment.fire_protection");
        put("40a9968f-5c66-4e2f-b7f4-2ec2f4b3e450", "minecraft:enchantment.blast_protection");
        put("07a65791-f64d-4e79-86c7-f83932f007ec", "minecraft:enchantment.respiration");
        put("60b1b7db-fffd-4ad0-817c-d6c6a93d8a45", "minecraft:enchantment.aqua_affinity");
        put("11dc269a-4476-46c0-aff3-9e17d7eb6801", "minecraft:enchantment.depth_strider");
        put("87f46a96-686f-4796-b035-22e16ee9e038", "minecraft:enchantment.soul_speed");
        put("b9716dbd-50df-4080-850e-70347d24e687", "minecraft:enchantment.soul_speed");
        put("92437d00-c3a7-4f2e-8f6c-1f21585d5dd0", "minecraft:enchantment.swift_sneak");
        put("5d3d087b-debe-4037-b53e-d84f3ff51f17", "minecraft:enchantment.sweeping_edge");
        put("3ceb37c0-db62-46b5-bd02-785457b01d96", "minecraft:enchantment.efficiency");
        put("CB3F55D3-645C-4F38-A497-9C13A33DB5CF", "minecraft:base_attack_damage");
        put("FA233E1C-4180-4865-B01B-BCCE9785ACA3", "minecraft:base_attack_speed");
    }
}
