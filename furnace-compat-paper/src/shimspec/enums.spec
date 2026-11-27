# 생성자 인자가 있는 열거형(T-230): 상수 줄을 `이름 = (인자)` 로 적는다. 생성자·필드·메서드는 src/shims/java 조각이 쓴다. 상수는 전부 바닐라 순서로.
class net.minecraft.network.protocol.PacketFlow
  SERVERBOUND = ("serverbound")
  CLIENTBOUND = ("clientbound")


# 직소 블록 엔티티: 바깥은 src/shims/java 조각, 안쪽 열거형 JointType 은 조각 안의 손 열거형(바닐라처럼 StringRepresentable 구현·이름 값 보유)
class net.minecraft.world.level.block.entity.JigsawBlockEntity

class net.minecraft.network.ConnectionProtocol
  HANDSHAKING = ("handshake")
  PLAY = ("play")
  STATUS = ("status")
  LOGIN = ("login")
  CONFIGURATION = ("configuration")

# 구조 블록 모드(패킷이 쓰는 열거형): 상수·인자는 명세, 생성자·필드·메서드는 src/shims/java 조각
class net.minecraft.world.level.block.state.properties.StructureMode
  SAVE = ("save")
  LOAD = ("load")
  CORNER = ("corner")
  DATA = ("data")

# 금고 상태: 상수마다 몸통이 있는 열거형(바닐라의 VaultState$1~4) — 상수 줄을 `= (인자) { 몸통 }` 로 적는다
class net.minecraft.world.level.block.entity.vault.VaultState
  INACTIVE = ("inactive") {}
  ACTIVE = ("active") {}
  UNLOCKING = ("unlocking") {}
  EJECTING = ("ejecting") {}
