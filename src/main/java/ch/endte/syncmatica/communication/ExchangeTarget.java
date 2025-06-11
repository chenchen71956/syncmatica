package ch.endte.syncmatica.communication;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.communication.exchange.Exchange;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;




public class ExchangeTarget {
    public final ClientPlayNetworkHandler clientPlayNetworkHandler;
    public final ServerPlayNetworkHandler serverPlayNetworkHandler;
    private final String persistentName;

    private FeatureSet features;
    private final List<Exchange> ongoingExchanges = new ArrayList<>(); 

    public ExchangeTarget(ClientPlayNetworkHandler clientPlayNetworkHandler) {
        this.clientPlayNetworkHandler = clientPlayNetworkHandler;
        this.serverPlayNetworkHandler = null;
        this.persistentName = StringUtils.getWorldOrServerName();
    }

    public ExchangeTarget(ServerPlayNetworkHandler serverPlayNetworkHandler) {
        this.clientPlayNetworkHandler = null;
        this.serverPlayNetworkHandler = serverPlayNetworkHandler;
        this.persistentName = serverPlayNetworkHandler.player.getUuidAsString();
    }

    
    
    public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
        if (context != null) {
            context.getDebugService().logSendPacket(id, persistentName);
        }
        if (clientPlayNetworkHandler != null) {
            CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(id, packetBuf);
            clientPlayNetworkHandler.sendPacket(packet);
        }
        if (serverPlayNetworkHandler != null) {
            CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(id, packetBuf);
            serverPlayNetworkHandler.sendPacket(packet);
        }
    }

    

    public FeatureSet getFeatureSet() {
        return features;
    }

    public void setFeatureSet(final FeatureSet f) {
        features = f;
    }

    public Collection<Exchange> getExchanges() {
        return ongoingExchanges;
    }

    public String getPersistentName() {
        return persistentName;
    }

    public boolean isServer() {
        return serverPlayNetworkHandler != null;
    }

    public boolean isClient() {
        return clientPlayNetworkHandler != null;
    }
}
