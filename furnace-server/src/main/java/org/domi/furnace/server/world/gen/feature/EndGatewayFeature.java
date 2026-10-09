// end_gateway — 바닐라 EndGatewayFeature 이식 (3x5x3 기반암 틀 + 중앙 end_gateway 블록, 블록 엔티티에 exit_portal/ExactTeleport 기록)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class EndGatewayFeature implements ConfiguredFeature {

    private final int gateway, bedrock, air;
    /** config.exit — 없으면 null. */
    private final int[] exit;
    private final boolean exact;

    public EndGatewayFeature(BlockRegistry registry, int[] exit, boolean exact) {
        this.gateway = registry.defaultStateId("minecraft:end_gateway");
        this.bedrock = registry.defaultStateId("minecraft:bedrock");
        this.air = registry.defaultStateId("minecraft:air");
        this.exit = exit;
        this.exact = exact;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int ox, int oy, int oz) {
        // BlockPos.betweenClosed(origin-(1,2,1), origin+(1,2,1)) — x 가 가장 안쪽, 다음 y, 바깥이 z.
        for (int z = oz - 1; z <= oz + 1; z++) {
            for (int y = oy - 2; y <= oy + 2; y++) {
                for (int x = ox - 1; x <= ox + 1; x++) {
                    boolean sameX = x == ox;
                    boolean sameY = y == oy;
                    boolean sameZ = z == oz;
                    boolean edgeY = Math.abs(y - oy) == 2;
                    if (sameX && sameY && sameZ) {
                        view.setBlock(x, y, z, gateway);
                        // setBlock 이 만든 TheEndGatewayBlockEntity 에 setExitPosition(exit, exact) — 월드젠 청크에 저장되는 NBT.
                        NbtCompound be = new NbtCompound();
                        be.putString("id", "minecraft:end_gateway");
                        be.putInt("x", x);
                        be.putInt("y", y);
                        be.putInt("z", z);
                        be.putLong("Age", 0L);
                        if (exit != null) {
                            be.put("exit_portal", new int[]{exit[0], exit[1], exit[2]});
                            if (exact) be.putByte("ExactTeleport", (byte) 1);
                        }
                        view.putGeneratedBlockEntity(x, y, z, be);
                    } else if (sameY) {
                        view.setBlock(x, y, z, air);
                    } else if (edgeY && sameX && sameZ) {
                        view.setBlock(x, y, z, bedrock);
                    } else if ((sameX || sameZ) && !edgeY) {
                        view.setBlock(x, y, z, bedrock);
                    } else {
                        view.setBlock(x, y, z, air);
                    }
                }
            }
        }
        return true;
    }
}
