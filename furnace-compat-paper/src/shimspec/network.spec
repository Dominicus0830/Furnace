# 네트워크 영역(영역 7) 손 shim 의 명세 이전(2026-11-24). 바닐라(Paper)에서 추상인 메서드는 이름만 적어 추상으로 둔다.
class net.minecraft.network.codec.StreamDecoder
  decode
class net.minecraft.network.codec.StreamEncoder
  encode
class net.minecraft.network.codec.StreamMemberEncoder
  encode
class net.minecraft.network.syncher.SyncedDataHolder
  onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V
  onSyncedDataUpdated(Ljava/util/List;)V
class net.minecraft.network.ProtocolInfo
  id
  flow
  codec
  bundlerInfo
class net.minecraft.network.ProtocolInfo$Unbound
  bind
  id
  flow
  listPackets
class net.minecraft.network.ProtocolInfo$Unbound$PacketVisitor
  accept
class net.minecraft.network.VarInt
  <init> -> @hand
  getByteSize -> @hand pure
  getByteSizeOld -> @hand pure
  hasContinuationBit -> @hand pure
  read -> @hand pure
  write -> @hand pure
  writeOld -> @hand pure
class net.minecraft.network.chat.ClickEvent
class net.minecraft.network.chat.HoverEvent
class net.minecraft.network.chat.Style
class net.minecraft.network.chat.TextColor
class net.minecraft.network.chat.CommonComponents
class net.minecraft.network.chat.ComponentUtils
class net.minecraft.network.chat.ComponentSerialization
class net.minecraft.network.chat.numbers.NumberFormat
class net.minecraft.network.chat.numbers.BlankFormat
class net.minecraft.network.chat.numbers.FixedFormat
class net.minecraft.network.chat.numbers.StyledFormat
