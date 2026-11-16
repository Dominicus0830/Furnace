// 플러그인 API 데이터 픽서(NbtDataFixer): 구조물 데이터 픽서의 고침 표를 API NBT 값에 적용한다. 입력은 건드리지 않고 사본을 고쳐 돌려준다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtDataFixer;
import org.domi.furnace.server.datafix.StructureDataFixer;

final class NbtDataFixerImpl implements NbtDataFixer {

    @Override public int currentVersion() { return StructureDataFixer.CURRENT_VERSION; }

    @Override public int minSupportedVersion() { return StructureDataFixer.MIN_SUPPORTED_VERSION; }

    @Override public boolean supports(int dataVersion) { return StructureDataFixer.supports(dataVersion); }

    @Override public String fixBlockName(String name, int fromVersion) { return StructureDataFixer.fixBlockName(name, fromVersion); }

    @Override public String fixItemName(String name, int fromVersion) { return StructureDataFixer.fixItemName(name, fromVersion); }

    @Override
    public NbtCompound fixBlockState(NbtCompound state, int fromVersion) {
        return ApiNbt.toApi(StructureDataFixer.fixBlockState(ApiNbt.toServer(state), fromVersion));
    }

    @Override
    public NbtCompound fixItemStack(NbtCompound item, int fromVersion) {
        return ApiNbt.toApi(StructureDataFixer.fixItem(ApiNbt.toServer(item), fromVersion));
    }

    @Override
    public NbtCompound fixBlockEntity(NbtCompound blockEntity, int fromVersion) {
        return ApiNbt.toApi(StructureDataFixer.fixBlockEntityPiece(ApiNbt.toServer(blockEntity), fromVersion));
    }

    @Override
    public NbtCompound fixEntity(NbtCompound entity, int fromVersion) {
        return ApiNbt.toApi(StructureDataFixer.fixEntityPiece(ApiNbt.toServer(entity), fromVersion));
    }
}
