// A06-07: generate-structures=false 면 바닐라는 STRUCTURE_STARTS 단계가 시작점을 만들지 않아 /locate 가 찾지 못한다.
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class LocateStructuresDisabledTest {

    private static NoiseWorldSource overworld() {
        return NoiseWorldSource.overworld(0L, BlockRegistry.load("blocks/1.21.1.txt"), RegistrySet.load("registries/1.21.1.txt"));
    }

    @Test
    void locateFindsNothingWhenStructuresDisabled() {
        NoiseWorldSource gen = overworld();
        gen.structureEngine().setStructuresEnabled(false);
        assertNull(gen.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100));
        assertNull(gen.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:eye_of_ender_located"), 0, 70, 0, 100));
    }

    @Test
    void locateStillFindsWhenEnabled() {
        assertNotNull(overworld().structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100));
    }
}
