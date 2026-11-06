/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserConnect
 */

package org.geysermc.extension.connect;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.geysermc.extension.connect.config.Config;
import org.geysermc.extension.connect.config.ConfigLoader;
import org.geysermc.extension.connect.storage.AbstractStorageManager;
import org.geysermc.extension.connect.storage.DisabledStorageManager;
import org.geysermc.extension.connect.utils.Utils;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.extension.connect.session.MenuSessionController;
import org.geysermc.extension.connect.ui.UIHandler;
import org.geysermc.extension.connect.utils.ServerManager;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GeyserConnect implements Extension {
    private static GeyserConnect instance;
    private Config config;
    private org.geysermc.extension.connect.i18n.LanguageService language = org.geysermc.extension.connect.i18n.LanguageService.builtinEnglish();
    public org.geysermc.extension.connect.i18n.LanguageService language() { return language; }
    public static String tr(String key) { return instance().language().text(key); }
    private AbstractStorageManager storageManager;
    private final Map<GeyserSession, MenuSessionController> menuSessions = new ConcurrentHashMap<>();

    public Optional<MenuSessionController> menuSession(GeyserSession session) {
        return Optional.ofNullable(menuSessions.get(session));
    }

    public List<MenuSessionController> menuSessions() { return List.copyOf(menuSessions.values()); }

    static boolean supportsPlatform(PlatformType platform) {
        return PlatformType.STANDALONE.equals(platform) || PlatformType.VIAPROXY.equals(platform);
    }

    public GeyserConnect() {
        instance = this;
    }

    public static GeyserConnect instance() {
        return instance;
    }

    public Config config() {
        return config;
    }

    public AbstractStorageManager storageManager() {
        return storageManager;
    }

    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        config = ConfigLoader.load(this, GeyserConnect.class, Config.class);
        if (config == null) { this.disable(); return; }
        try {
            language = org.geysermc.extension.connect.i18n.LanguageService.load(dataFolder(), config.languageFile(), warning -> logger().warning(warning));
        } catch (java.io.IOException error) { logger().error(org.geysermc.extension.connect.GeyserConnect.tr("error.language-init"), error); this.disable(); return; }
        if (!supportsPlatform(this.geyserApi().platformType())) {
            this.logger().severe(org.geysermc.extension.connect.GeyserConnect.tr("error.platform"));
            this.disable();
        }
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        config = ConfigLoader.load(this, GeyserConnect.class, Config.class);

        if (!config.customServers().enabled()) {
            // Force the storage manager if we have it disabled
            storageManager = new DisabledStorageManager();
            this.logger().info(org.geysermc.extension.connect.GeyserConnect.tr("log.custom-disabled"));
        } else {
            try {
                storageManager = config.customServers().storageType().storageManager().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                this.logger().severe(org.geysermc.extension.connect.GeyserConnect.tr("error.storage-type"), e);
                return;
            }
        }

        try { storageManager.setupStorage(); }
        catch (RuntimeException error) { logger().error(tr("error.database-connect"),error); disable(); return; }
        long hidden=config.servers().stream().filter(server -> !server.bedrock()).count();
        if(hidden>0) logger().warning(language.text("log.legacy",java.util.Map.of("count",hidden)));

        GeyserImpl geyserInstance = (GeyserImpl) this.geyserApi();

        if (geyserInstance.config().motd().passthroughMotd() || geyserInstance.config().motd().passthroughPlayerCounts()) {
            this.logger().warning(org.geysermc.extension.connect.GeyserConnect.tr("log.passthrough"));
        }

    }

    @Subscribe
    public void onSessionInitialize(SessionInitializeEvent event) {
        GeyserSession session = (GeyserSession) event.connection();
        if (config().hardPlayerLimit()) {
            if (session.getGeyser().getSessionManager().size() > session.getGeyser().config().motd().maxPlayers()) {
                session.disconnect("disconnectionScreen.serverFull");
            }
        }

    }

    @Subscribe
    public void onResourcePacks(SessionLoadResourcePacksEvent event) {
        GeyserSession session = (GeyserSession) event.connection();
        if (menuSessions.containsKey(session)) return;
        BedrockPacketHandler packetHandler = session.getUpstream().getSession().getPacketHandler();
        MenuSessionController controller = new MenuSessionController(session, () -> {
            try {
                ServerManager.loadServers(session);
                new UIHandler(session, menuSessions.get(session)).initialiseSession();
            } catch (RuntimeException error) {
                logger().error(tr("error.load-player"),error);
                session.disconnect(tr("error.load-player"));
            }
        }, () -> {
            try { ServerManager.unloadServers(session); }
            finally { menuSessions.remove(session); }
        });
        menuSessions.put(session, controller);
        session.getUpstream().getSession().setPacketHandler(new PacketHandler(packetHandler, controller));
    }

    @Subscribe
    public void onCommandDefine(GeyserDefineCommandsEvent event) {
        event.register(Command.builder(this).source(GeyserConnection.class).playerOnly(true).bedrockOnly(true)
            .name("menu").description(tr("command.menu-description"))
            .executor((source, command, args) -> {
                GeyserSession session=(GeyserSession)source;
                menuSession(session).filter(c -> c.state()==org.geysermc.extension.connect.session.MenuState.MENU_READY)
                    .ifPresent(c -> new UIHandler(session,menuSessions.get(session)).sendMainMenu());
            }).build());
        event.register(Command.builder(this).source(CommandSource.class).name("messageall")
            .description(tr("command.message-description")).executor((source, command, args) -> {
                if(!source.isConsole()) { source.sendMessage(tr("command.console-only")); return; }
                if(args.length<2) { source.sendMessage(tr("command.missing-message")); return; }
                String type=args[0].toLowerCase(java.util.Locale.ROOT);
                if(!type.equals("chat") && !type.equals("gui")) { source.sendMessage(tr("command.invalid-type")); return; }
                String message=Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
                for(var controller:menuSessions()) {
                    if(controller.state()!=org.geysermc.extension.connect.session.MenuState.MENU_READY) continue;
                    controller.session().executeInEventLoop(() -> {
                        if(controller.state()!=org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                        if(type.equals("chat")) controller.session().sendMessage(message);
                        else controller.session().sendForm(CustomForm.builder().title(tr("notice.title")).label(message).build());
                    });
                }
            }).build());
        event.register(Command.builder(this).source(CommandSource.class).name("transferall")
            .description(tr("command.transfer-description")).executor((source,command,args)-> {
                if(!source.isConsole()) { source.sendMessage(tr("command.console-only")); return; }
                if(args.length!=1) { source.sendMessage(tr("command.transfer-usage")); return; }
                try {
                    var target=org.geysermc.extension.connect.utils.BedrockEndpoint.fromCommand(args[0]);
                    var server=new org.geysermc.extension.connect.utils.Server(target.address(),target.port(),false,true,null,null,null);
                    for(var controller:menuSessions()) controller.session().executeInEventLoop(() -> Utils.sendToServer(controller.session(),server));
                } catch(IllegalArgumentException error) { source.sendMessage(tr("error.input")); }
            }).build());
    }

    private List<GeyserSession> getGeyserSessions() {
        return this.geyserApi().onlineConnections().stream().map(connection -> (GeyserSession) connection).toList();
    }
}
