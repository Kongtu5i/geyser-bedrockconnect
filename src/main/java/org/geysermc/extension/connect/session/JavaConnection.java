package org.geysermc.extension.connect.session;

import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.api.network.AuthType;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;

/** Resumes the host's authentication in the world already prepared for the menu. */
public final class JavaConnection {
    private JavaConnection() {}

    public static void connect(GeyserSession session) {
        // Match UpstreamPacketHandler's authentication selection, without calling
        // connect() again: the menu has already spawned Geyser's waiting world.
        var geyser = session.getGeyser();
        String username = session.getAuthData().name();
        if (geyser.config().java().authType() != AuthType.ONLINE) {
            session.authenticate(username);
        } else {
            String authChain = geyser.config().savedUserLogins().contains(username)
                ? geyser.authChainFor(username) : null;
            if (authChain != null) {
                SavedJavaLogin.connect(session, authChain);
            } else {
                var task = geyser.getPendingMicrosoftAuthentication().getTask(session.getAuthData().xuid());
                if (task != null && task.getAuthentication().isDone()) session.onMicrosoftLoginComplete(task);
            }
        }

        // The menu consumed the client's initialization packet. Let the original
        // host translator now run login/consent UI, join events and player-loaded
        // handling. PacketHandler delegates it after the one-way state change.
        session.getUpstream().setInitialized(false);
        var initialized = new SetLocalPlayerAsInitializedPacket();
        initialized.setRuntimeEntityId(session.getPlayerEntity().geyserId());
        session.getUpstream().getSession().getPacketHandler().handlePacket(initialized);
    }
}
