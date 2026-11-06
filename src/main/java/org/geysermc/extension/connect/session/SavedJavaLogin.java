package org.geysermc.extension.connect.session;

import org.geysermc.geyser.session.GeyserSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.util.MinecraftAuth4To5Migrator;
import com.google.gson.JsonObject;

final class SavedJavaLogin {
    private static final long MIN_VALIDITY_MS = 60_000;
    record Credentials(String chain, long expiresAt) {}
    private SavedJavaLogin() {}

    static void connect(GeyserSession session, String chain) {
        refresh(chain).whenComplete((credentials, error) -> session.executeInEventLoop(() -> {
            if (session.isClosed()) return;
            if (error != null || credentials.expiresAt() - System.currentTimeMillis() < MIN_VALIDITY_MS) {
                LoginEncryptionUtils.buildAndShowTokenExpiredWindow(session);
                return;
            }
            session.authenticateWithAuthChain(credentials.chain());
        }));
    }

    static CompletableFuture<Credentials> refresh(String chain) {
        // Geyser's saved-login failure callback calls connect() unconditionally.
        // Refresh here first so failure can reuse the already spawned menu world.
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject json = GeyserImpl.GSON.fromJson(chain, JsonObject.class);
                if (json.has("mcProfile")) json = MinecraftAuth4To5Migrator.migrateJavaSave(json, GeyserImpl.OAUTH_CONFIG);
                var auth = JavaAuthManager.fromJson(PendingMicrosoftAuthentication.AUTH_CLIENT, json);
                auth.getMinecraftProfile().getUpToDate();
                var token = auth.getMinecraftToken().getUpToDate();
                if (token.getExpireTimeMs() - System.currentTimeMillis() < MIN_VALIDITY_MS) {
                    token = auth.getMinecraftToken().refresh();
                }
                return new Credentials(GeyserImpl.GSON.toJson(JavaAuthManager.toJson(auth)), token.getExpireTimeMs());
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
    }
}
