package ch.endte.syncmatica.communication.exchange;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.communication.PacketType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.*;




public class UploadExchange extends AbstractExchange {

    
    
    private static final int BUFFER_SIZE = 16384;

    private final ServerPlacement toUpload;
    private final InputStream inputStream;
    private final byte[] buffer = new byte[BUFFER_SIZE];

    public UploadExchange(final ServerPlacement syncmatic, final File uploadFile, final ExchangeTarget partner, final Context con) throws FileNotFoundException {
        super(partner, con);
        toUpload = syncmatic;
        inputStream = new FileInputStream(uploadFile);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        if (id.equals(PacketType.RECEIVED_LITEMATIC.identifier)
                || id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            return checkUUID(packetBuf, toUpload.getId());
        }
        return false;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {

        packetBuf.readUuid(); 
        if (id.equals(PacketType.RECEIVED_LITEMATIC.identifier)) {
            send();
        }
        if (id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            close(false);
        }
    }

    private void send() {
        
        final int bytesRead;
        try {
            bytesRead = inputStream.read(buffer);
        } catch (final IOException e) {
            close(true);
            e.printStackTrace();
            return;
        }
        if (bytesRead == -1) {
            sendFinish();
        } else {
            sendData(bytesRead);
        }
    }

    private void sendData(final int bytesRead) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        packetByteBuf.writeInt(bytesRead);
        packetByteBuf.writeBytes(buffer, 0, bytesRead);
        getPartner().sendPacket(PacketType.SEND_LITEMATIC.identifier, packetByteBuf, getContext());
    }

    private void sendFinish() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        getPartner().sendPacket(PacketType.FINISHED_LITEMATIC.identifier, packetByteBuf, getContext());
        succeed();
    }

    @Override
    public void init() {
        send();
    }

    @Override
    protected void onClose() {
        try {
            inputStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void sendCancelPacket() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        getPartner().sendPacket(PacketType.CANCEL_LITEMATIC.identifier, packetByteBuf, getContext());
    }

}
