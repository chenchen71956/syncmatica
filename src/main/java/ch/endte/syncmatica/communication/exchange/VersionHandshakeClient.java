package ch.endte.syncmatica.communication.exchange;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.communication.FeatureSet;
import ch.endte.syncmatica.communication.PacketType;
import ch.endte.syncmatica.litematica.LitematicManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;

public class VersionHandshakeClient extends FeatureExchange {

    private String partnerVersion;

    public VersionHandshakeClient(final ExchangeTarget partner, final Context con) {
        super(partner, con);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        return id.equals(PacketType.CONFIRM_USER.identifier)
                || id.equals(PacketType.REGISTER_VERSION.identifier)
                || super.checkPacket(id, packetBuf);
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        if (id.equals(PacketType.REGISTER_VERSION.identifier)) {
            final String version = packetBuf.readString(32767);
            if (!getContext().checkPartnerVersion(version)) {
                
                LogManager.getLogger(VersionHandshakeClient.class).info("Denying syncmatica join due to outdated server with local version {} and server version {}", Syncmatica.VERSION, version);
                close(false);
            } else {
                partnerVersion = version;
                final FeatureSet fs = FeatureSet.fromVersionString(version);
                if (fs == null) {
                    requestFeatureSet();
                } else {
                    getPartner().setFeatureSet(fs);
                    onFeatureSetReceive();
                }
            }
        } else if (id.equals(PacketType.CONFIRM_USER.identifier)) {
            final int placementCount = packetBuf.readInt();
            for (int i = 0; i < placementCount; i++) {
                final ServerPlacement p = getManager().receiveMetaData(packetBuf, getPartner());
                getContext().getSyncmaticManager().addPlacement(p);
            }
            LogManager.getLogger(VersionHandshakeClient.class).info("Joining syncmatica server with local version {} and server version {}", Syncmatica.VERSION, partnerVersion);
            LitematicManager.getInstance().commitLoad();
            getContext().startup();
            succeed();
        } else {
            super.handle(id, packetBuf);
        }
    }

    @Override
    public void onFeatureSetReceive() {
        final PacketByteBuf newBuf = new PacketByteBuf(Unpooled.buffer());
        newBuf.writeString(Syncmatica.VERSION);
        getPartner().sendPacket(PacketType.REGISTER_VERSION.identifier, newBuf, getContext());
    }

    @Override
    public void init() {
        
    }

}
