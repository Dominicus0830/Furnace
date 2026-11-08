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
        super.enableReplaceObject(true);
    }

    public BukkitObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        super.enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (!(obj instanceof Serializable) && (obj instanceof ConfigurationSerializable)) {
            obj = Wrapper.newWrapper((ConfigurationSerializable) obj);
        }
        return super.replaceObject(obj);
    }
}
