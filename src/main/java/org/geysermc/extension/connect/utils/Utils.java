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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.geysermc.api.connection.Connection;
import org.geysermc.extension.connect.GeyserConnect;
import org.geysermc.geyser.session.GeyserSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class Utils {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<Server> getServers(ServerCategory category) {
        return GeyserConnect.instance().config().servers().stream().filter(server -> server.bedrock() && server.category() == category).toList();
    }

    public static File fileOrCopiedFromResource(String fileName, String name) throws IOException {
        File file = GeyserConnect.instance().dataFolder().resolve(fileName).toFile();

        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(new File(GeyserConnect.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath(), Collections.emptyMap())) {
                    try (InputStream input = Files.newInputStream(fileSystem.getPath(name))) {
                        byte[] bytes = new byte[input.available()];

                        input.read(bytes);

                        writer.write(new String(bytes).toCharArray());

                        writer.flush();
                    }
                }
            } catch (URISyntaxException ignored) {
            }
        }

        return file;
    }

    public static String displayName(Connection session) {
        return session.bedrockUsername() + " (" + session.xuid() + ")";
    }

    public static boolean sendToServer(GeyserSession session, Server server) {
        if (!server.bedrock()) throw new IllegalArgumentException("Bedrock target required");
        var extension = GeyserConnect.instance();
        var controller = extension.menuSession(session).orElse(null);
        if (controller == null || !controller.tryBeginTransfer()) return false;
        try {
            ServerManager.saveServers(session);
            TransferPacket packet = new TransferPacket();
            packet.setAddress(server.address());
            packet.setPort(server.port());
            session.sendUpstreamPacket(packet);
            extension.logger().info(extension.language().text("log.transferring", java.util.Map.of(
                "player", session.bedrockUsername(), "address", server.address(), "port", server.port())));
            return true;
        } catch (RuntimeException error) {
            controller.transferFailed();
            session.sendMessage(extension.language().text("error.transfer"));
            extension.logger().error(extension.language().text("error.transfer"), error);
            return false;
        }
    }

    /**
     * Read all bytes from a file
     *
     * @param file File to read bytes of
     * @return The byte array of the file
     */
    public static byte[] readAllBytes(File file) {
        try (InputStream stream = new FileInputStream(file)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + file);
        }
    }
}
