package net.minecraft.network.syncher;

import java.util.List;

/** 메타데이터를 가진 쪽(Mojang 이름 shim, 바닐라와 같은 두 훅). ProtocolLib 이 생성자 인자 종류를 이 이름으로 찾는다. */
public interface SyncedDataHolder {

    /** 칸 하나가 바뀌었다({@link SynchedEntityData#set} 이 값을 넣은 뒤, 더러움 표시 전에 부른다). */
    void onSyncedDataUpdated(EntityDataAccessor<?> accessor);

    /** 받은 값 묶음(클라이언트 쪽 assignValues). */
    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values);
}
