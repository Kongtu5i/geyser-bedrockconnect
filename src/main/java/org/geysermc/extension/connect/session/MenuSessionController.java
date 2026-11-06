package org.geysermc.extension.connect.session;

import org.geysermc.geyser.session.GeyserSession;

/** All calls are made on the connection's event loop. */
public final class MenuSessionController {
    private final GeyserSession session;
    private final Runnable showMenu;
    private final Runnable cleanup;
    private volatile MenuState state = MenuState.PREPARING;
    private boolean worldPrepared;

    public MenuSessionController(GeyserSession session, Runnable showMenu, Runnable cleanup) {
        this.session = session;
        this.showMenu = showMenu;
        this.cleanup = cleanup;
    }

    public void onResourcePacksComplete() {
        if (state != MenuState.PREPARING || worldPrepared) return;
        worldPrepared = true;
        session.connect();
    }

    public void onPlayerInitialized(long runtimeEntityId) {
        if (state != MenuState.PREPARING || !worldPrepared ||
            runtimeEntityId != session.getPlayerEntity().geyserId()) return;
        session.getUpstream().setInitialized(true);
        state = MenuState.MENU_READY;
        showMenu.run();
    }

    public boolean tryBeginTransfer() {
        if (state != MenuState.MENU_READY) return false;
        state = MenuState.TRANSFERRING;
        return true;
    }

    public void connectToJavaServer() {
        if (state != MenuState.MENU_READY) return;
        state = MenuState.JAVA_CONNECTING;
        try {
            JavaConnection.connect(session);
        } catch (RuntimeException error) {
            var extension = org.geysermc.extension.connect.GeyserConnect.instance();
            extension.logger().error(org.geysermc.extension.connect.GeyserConnect.tr("error.java-connect"), error);
            session.disconnect(org.geysermc.extension.connect.GeyserConnect.tr("error.java-connect"));
        }
    }

    public void transferFailed() {
        if (state == MenuState.TRANSFERRING) state = MenuState.MENU_READY;
    }

    public void close() {
        if (state == MenuState.CLOSED) return;
        state = MenuState.CLOSED;
        cleanup.run();
    }

    public MenuState state() { return state; }
    public GeyserSession session() { return session; }
}
