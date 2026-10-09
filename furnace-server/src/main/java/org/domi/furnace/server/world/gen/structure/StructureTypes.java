// 구조물 type 이름 → 구현 등록부.
package org.domi.furnace.server.world.gen.structure;

final class StructureTypes {
    private StructureTypes() {}

    static void registerAll(StructureEngine e) {
        e.registerType("minecraft:igloo", IglooStructure::new);
        e.registerType("minecraft:jungle_temple", d -> JungleTempleStructure.type());
        e.registerType("minecraft:ruined_portal", RuinedPortalStructure::new);
        e.registerType("minecraft:ocean_ruin", OceanRuinStructure::new);
        e.registerType("minecraft:buried_treasure", BuriedTreasureStructure::new);
        e.registerType("minecraft:shipwreck", ShipwreckStructure::new);
        e.registerType("minecraft:desert_pyramid", d -> new SinglePieceStructure(
            (en, r, x, z) -> new DesertPyramidPiece(r, x, z), DesertPyramidPiece.WIDTH, DesertPyramidPiece.DEPTH));
    }
}
