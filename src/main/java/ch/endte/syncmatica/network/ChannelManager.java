package ch.endte.syncmatica.network;

import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.communication.PacketType;
import ch.endte.syncmatica.util.StringTools;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChannelManager {
    private static final Identifier MINECRAFT_REGISTER = new Identifier("minecraft:register");
    private static final Identifier MINECRAFT_UNREGISTER = new Identifier("minecraft:unregister");
    private static final List<Identifier> serverRegisterChannels = new ArrayList<>();
    private static final List<Identifier> clientRegisterChannels = new ArrayList<>();

    private static List<Identifier> onReadRegisterIdentifier(PacketByteBuf data) {
        List<Identifier> identifiers = new ArrayList<>();
        int start = 0;
        while (data.isReadable()) {
            byte b = data.readByte();
            if (b == 0x00) {
                String string = data.toString(start, data.readerIndex() - start - 1, StandardCharsets.UTF_8);
                string = string.split("/")[0].split("\\\\")[0];
                identifiers.add(new Identifier(string));
                start = data.readerIndex();
            }
        }
        return identifiers;
    }

    public static void onChannelRegisterHandle(ExchangeTarget target, Identifier channel, PacketByteBuf data) {
        if (channel.equals(MINECRAFT_REGISTER)) {
            
            List<Identifier> identifiers = onReadRegisterIdentifier(new PacketByteBuf(data.copy()));
            
            PacketByteBuf byteBuf2 = new PacketByteBuf(Unpooled.buffer());
            List<Identifier> registerChannels = target.isClient() ? clientRegisterChannels : serverRegisterChannels;
            for (Identifier identifier : identifiers) {
                if (!registerChannels.contains(identifier) && PacketType.containsIdentifier(identifier)) {
                    LoggerFactory.getLogger("").info(identifier.toString());
                    byte[] bytes = identifier.toString().getBytes(StandardCharsets.UTF_8);
                    byteBuf2.writeBytes(bytes);
                    byteBuf2.writeByte(0x00);
                }
            }
            
            if (byteBuf2.writerIndex() > 0) {
                target.sendPacket(MINECRAFT_REGISTER, byteBuf2, null);
            }
        }



    }


    public static void onDisconnected() {
        clientRegisterChannels.clear();
        serverRegisterChannels.clear();
    }
}
