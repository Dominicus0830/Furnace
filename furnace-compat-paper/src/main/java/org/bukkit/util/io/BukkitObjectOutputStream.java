// Bukkit BukkitObjectOutputStream — 서명만 Bukkit 에 맞추고 몸통은 Furnace 가 새로 썼다.
package org.bukkit.util.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * ConfigurationSerializable API 와 함께 쓰는 객체 출력 스트림: Serializable 이 아닌 ConfigurationSerializable(ItemStack·ItemMeta 등)을
 * 내부 껍데기로 바꿔 쓰고, {@link BukkitObjectInputStream} 이 되읽는다. 내부 형태는 버전이 바뀌어도 같다고 보장하지 않는다.
 */
public class BukkitObjectOutputStream extends ObjectOutputStream {

    protected BukkitObjectOutputStream() throws IOException, SecurityException {
        super();
        enableReplaceObject(true);
    }

    public BukkitObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
        Object out = obj instanceof ConfigurationSerializable cs && !(obj instanceof Serializable) ? Wrapper.newWrapper(cs) : obj;
        return super.replaceObject(out);
    }
}
