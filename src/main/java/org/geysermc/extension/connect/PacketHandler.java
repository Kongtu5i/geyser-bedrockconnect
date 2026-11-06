package org.geysermc.extension.connect;

import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.extension.connect.session.MenuSessionController;

public final class PacketHandler implements BedrockPacketHandler {
    private final BedrockPacketHandler original;
    private final MenuSessionController controller;
    private boolean stackRequested;

    public PacketHandler(BedrockPacketHandler original, MenuSessionController controller) {
        this.original = original;
        this.controller = controller;
    }

    @Override public PacketSignal handlePacket(BedrockPacket packet) {
        if (controller.state() == org.geysermc.extension.connect.session.MenuState.JAVA_CONNECTING) {
            if (packet instanceof ResourcePackClientResponsePacket response &&
                response.getStatus() == ResourcePackClientResponsePacket.Status.COMPLETED) return PacketSignal.HANDLED;
            return original.handlePacket(packet);
        }
        if (packet instanceof ResourcePackClientResponsePacket response) {
            if (response.getStatus() == ResourcePackClientResponsePacket.Status.COMPLETED) {
                if (stackRequested) controller.session().executeInEventLoop(controller::onResourcePacksComplete);
                else controller.session().disconnect(org.geysermc.extension.connect.GeyserConnect.tr("error.pack-sequence"));
                return PacketSignal.HANDLED;
            }
            if (response.getStatus() == ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS)
                stackRequested = true;
        }
        if (packet instanceof SetLocalPlayerAsInitializedPacket initialized) {
            controller.session().executeInEventLoop(() -> controller.onPlayerInitialized(initialized.getRuntimeEntityId()));
            return PacketSignal.HANDLED;
        }
        return original.handlePacket(packet);
    }

    @Override public void onDisconnect(CharSequence reason) {
        controller.session().executeInEventLoop(() -> {
            try { controller.close(); }
            finally { original.onDisconnect(reason); }
        });
    }
}
