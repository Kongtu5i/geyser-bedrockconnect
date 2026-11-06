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

package org.geysermc.extension.connect.utils;

import org.geysermc.api.connection.Connection;
import org.geysermc.extension.connect.GeyserConnect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerManager {
    private static final Map<String, PlayerServerList> servers = new java.util.concurrent.ConcurrentHashMap<>();

    public static void loadServers(Connection session) {
        GeyserConnect.instance().logger().debug(GeyserConnect.tr("log.loading") + " " + Utils.displayName(session));
        var records = new PlayerServerList(GeyserConnect.instance().storageManager().loadServers(session));
        servers.put(session.xuid(), records);
        int hidden = records.all().size() - records.visible().size();
        if (hidden > 0 && session instanceof org.geysermc.geyser.session.GeyserSession geyserSession) geyserSession.sendMessage(GeyserConnect.instance().language().text("log.legacy", java.util.Map.of("count", hidden)));
    }

    public static void unloadServers(Connection session) {
        if (getServers(session) == null) return;
        GeyserConnect.instance().logger().debug(GeyserConnect.tr("log.unloading") + " " + Utils.displayName(session));
        try { saveServers(session); } finally { servers.remove(session.xuid()); }
    }

    public static List<Server> getServers(Connection session) {
        var list = servers.get(session.xuid());
        return list == null ? null : list.visible();
    }

    public static List<Server> getAllServers(Connection session) {
        var list = servers.get(session.xuid());
        return list == null ? List.of() : list.all();
    }

    public static void saveServers(Connection session) {
        var list = servers.get(session.xuid());
        if (list != null && list.isDirty()) {
            GeyserConnect.instance().storageManager().saveServers(session);
            list.markSaved();
        }
    }

    public static void addServer(Connection session, Server server) {
        servers.get(session.xuid()).add(server, GeyserConnect.instance().config().customServers().max());
    }

    public static void removeServer(Connection session, Server server) {
        servers.get(session.xuid()).remove(getServerIndex(session, server));
    }

    public static int getServerIndex(Connection session, Server server) {
        return getServers(session).indexOf(server);
    }

    public static void updateServer(Connection session, int serverIndex, Server server) {
        servers.get(session.xuid()).replace(serverIndex, server);
    }
}
