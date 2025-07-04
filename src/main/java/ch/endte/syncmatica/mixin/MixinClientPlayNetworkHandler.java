package ch.endte.syncmatica.mixin;

import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.mixin_actor.ActorClientPlayNetworkHandler;
import ch.endte.syncmatica.network.ChannelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = ClientPlayNetworkHandler.class, priority = 998)
public abstract class MixinClientPlayNetworkHandler {
    @Unique
    public ExchangeTarget exTarget = null;

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void handlePacket(final CustomPayloadS2CPacket packet, final CallbackInfo ci) {
        ChannelManager.onChannelRegisterHandle(getExchangeTarget(), packet.getChannel(), packet.getData());
        if (!MinecraftClient.getInstance().isOnThread()) {
            return; 
        }
        ActorClientPlayNetworkHandler.getInstance().packetEvent((ClientPlayNetworkHandler) (Object) this, packet, ci);
    }

    @Unique
    private ExchangeTarget getExchangeTarget() {
        if (exTarget == null) {
            exTarget = new ExchangeTarget((ClientPlayNetworkHandler) (Object) this);
        }
        return exTarget;
    }
}
