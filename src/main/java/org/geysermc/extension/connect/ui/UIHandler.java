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

package org.geysermc.extension.connect.ui;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.geysermc.extension.connect.GeyserConnect;
import org.geysermc.extension.connect.utils.Server;
import org.geysermc.extension.connect.utils.ServerCategory;
import org.geysermc.extension.connect.utils.ServerManager;
import org.geysermc.extension.connect.utils.Utils;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.session.GeyserSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class UIHandler {
    private final GeyserSession session;
    private final org.geysermc.extension.connect.session.MenuSessionController controller;

    public UIHandler(GeyserSession session, org.geysermc.extension.connect.session.MenuSessionController controller) {
        this.session = session;
        this.controller = controller;
    }

    public void initialiseSession() {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        String message = "";
        try {
            File messageFile = Utils.fileOrCopiedFromResource(GeyserConnect.instance().config().welcomeFile(), "welcome.txt");
            message = new String(Utils.readAllBytes(messageFile), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }

        if (!message.trim().isEmpty()) {
            session.sendForm(CustomForm.builder()
                .title(org.geysermc.extension.connect.GeyserConnect.tr("notice.title"))
                .label(message)
                .resultHandler((customForm, customFormResponseFormResponseResult) -> {
                    sendMainMenu();
                })
                .build());
        } else {
            sendMainMenu();
        }
    }

    public void sendMainMenu() {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        List<Server> stored = ServerManager.getServers(session);
        List<Server> configured = GeyserConnect.instance().config().servers();
        List<Server> servers = java.util.stream.Stream.concat(
            configured == null ? java.util.stream.Stream.empty() : configured.stream().filter(Server::bedrock),
            stored == null ? java.util.stream.Stream.empty() : stored.stream()).toList();
        boolean editable = GeyserConnect.instance().config().customServers().enabled();
        SimpleForm.Builder menu = SimpleForm.builder().title(GeyserConnect.tr("menu.main-title"));
        menu.button(GeyserConnect.tr("menu.java-server"));
        for (Server server : servers) menu.button(server.title(), server.formImage());
        if (editable) menu.button(GeyserConnect.tr("menu.edit-servers"));
        menu.button(GeyserConnect.tr("menu.disconnect"))
            .closedResultHandler(response -> sendMainMenu())
            .invalidResultHandler(response -> session.disconnect("disconnectionScreen.disconnected"))
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                int selected = response.clickedButtonId();
                if (selected == 0) {
                    if (controller != null) session.executeInEventLoop(controller::connectToJavaServer);
                    return;
                }
                selected--;
                if (selected < servers.size()) {
                    Utils.sendToServer(session, servers.get(selected));
                } else if (editable && selected == servers.size()) {
                    sendEditServersMenu();
                } else {
                    session.disconnect("disconnectionScreen.disconnected");
                }
            });
        session.sendForm(menu);
    }

    public void sendEditServersMenu() {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        SimpleForm.Builder editServersMenu = SimpleForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.edit-servers"))
            .content(org.geysermc.extension.connect.GeyserConnect.tr("menu.select-server"));

        List<Server> servers = ServerManager.getServers(session);

        for (Server server : servers) {
            editServersMenu.button(server.title(), server.formImage());
        }

        editServersMenu
            .button(org.geysermc.extension.connect.GeyserConnect.tr("menu.add-server"))
            .button(org.geysermc.extension.connect.GeyserConnect.tr("menu.back"))
            .closedOrInvalidResultHandler(response -> {
                sendMainMenu();
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                if (response.clickedButtonId() == servers.size()) {
                    sendAddServerMenu();
                    return;
                } else if (response.clickedButtonId() == servers.size() + 1) {
                    sendMainMenu();
                    return;
                }

                Server server = servers.get(response.clickedButtonId());
                sendServerOptionsMenu(server);
            });

        session.sendForm(editServersMenu);
    }

    public void sendAddServerMenu() {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        session.sendForm(CustomForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.add-server"))
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.address"), "play.cubecraft.net")
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.port"), "19132", "19132")
            .input(GeyserConnect.tr("form.name"), GeyserConnect.tr("form.name-placeholder"))
            .closedOrInvalidResultHandler(response -> {
                sendEditServersMenu();
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                String ip = response.asInput(0);
                var endpoint = parseEndpoint(ip, response.asInput(1));
                if (endpoint == null) return;
                ip = endpoint.address();
                int port = endpoint.port();

                Server server = new Server(ip, port, false, true, optionalName(response.asInput(2)), null, ServerCategory.CUSTOM);
                try { ServerManager.addServer(session, server); }
                catch (IllegalArgumentException error) { session.sendMessage(GeyserConnect.tr("error.input")); }
                sendEditServersMenu();
            }));
    }

    public void sendServerOptionsMenu(Server server) {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        session.sendForm(SimpleForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.server-options"))
            .content(server.title())
            .button(org.geysermc.extension.connect.GeyserConnect.tr("menu.edit-server"))
            .button(org.geysermc.extension.connect.GeyserConnect.tr("menu.delete-server"))
            .button(org.geysermc.extension.connect.GeyserConnect.tr("menu.back"))
            .closedOrInvalidResultHandler(response -> {
                sendEditServersMenu();
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                switch (response.clickedButtonId()) {
                    case 0:
                        sendEditServerMenu(server);
                        return;
                    case 1:
                        sendDeleteServerMenu(server);
                        return;
                    case 2:
                        sendEditServersMenu();
                        return;
                }
            }));
    }

    public void sendEditServerMenu(Server server) {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        int serverIndex = ServerManager.getServerIndex(session, server);
        session.sendForm(CustomForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.edit-server"))
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.address"), server.address(), server.address())
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.port"), String.valueOf(server.port()), String.valueOf(server.port()))
            .input(GeyserConnect.tr("form.name"), GeyserConnect.tr("form.name-placeholder"), server.name() == null ? "" : server.name())
            .closedOrInvalidResultHandler(response -> {
                sendServerOptionsMenu(server);
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                String ip = response.asInput(0);
                var endpoint = parseEndpoint(ip, response.asInput(1));
                if (endpoint == null) return;
                ip = endpoint.address();
                int port = endpoint.port();

                Server newServer = new Server(ip, port, false, true, optionalName(response.asInput(2)), server.imageUrl(), ServerCategory.CUSTOM);
                ServerManager.updateServer(session, serverIndex, newServer);
                sendServerOptionsMenu(newServer);
            }));
    }

    public void sendDeleteServerMenu(Server server) {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        session.sendForm(ModalForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.delete-server"))
            .content(GeyserConnect.instance().language().text("form.confirm-delete", java.util.Map.of("server", server.title())))
            .button1(org.geysermc.extension.connect.GeyserConnect.tr("form.yes"))
            .button2(org.geysermc.extension.connect.GeyserConnect.tr("form.no"))
            .closedOrInvalidResultHandler(response -> {
                sendServerOptionsMenu(server);
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                if (response.clickedButtonId() == 0) {
                    ServerManager.removeServer(session, server);
                }
                sendEditServersMenu();
            }));
    }

    private static String optionalName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private org.geysermc.extension.connect.utils.BedrockEndpoint parseEndpoint(String address, String port) {
        try { return org.geysermc.extension.connect.utils.BedrockEndpoint.fromForm(address,port); }
        catch (IllegalArgumentException error) {
            session.sendMessage(GeyserConnect.tr("error.input"));
            sendMainMenu();
            return null;
        }
    }

    public void sendDirectConnectMenu() {
        if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
        session.sendForm(CustomForm.builder()
            .title(org.geysermc.extension.connect.GeyserConnect.tr("menu.direct-connect"))
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.address"), "play.cubecraft.net")
            .input(org.geysermc.extension.connect.GeyserConnect.tr("form.port"), "19132", "19132")
            .closedOrInvalidResultHandler(response -> {
                sendMainMenu();
            })
            .validResultHandler(response -> {
                if (controller != null && controller.state() != org.geysermc.extension.connect.session.MenuState.MENU_READY) return;
                String ip = response.asInput(0);
                var endpoint = parseEndpoint(ip, response.asInput(1));
                if (endpoint == null) return;
                ip = endpoint.address();
                int port = endpoint.port();

                Server server = new Server(ip, port, false, true, null, null, ServerCategory.CUSTOM);
                Utils.sendToServer(session, server);
            }));
    }
}
