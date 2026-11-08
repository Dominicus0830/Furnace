package org.domi.furnace.compat.nms;

import org.domi.furnace.api.packet.ClientboundPacket;

/** 이 모듈이 만든 clientbound 패킷 shim: 보낼 때 Furnace 패킷 API 레코드로 바뀐다(H7c). 플러그인용이 아니다. */
public interface NmsPacket {

    ClientboundPacket toFurnace();
}
