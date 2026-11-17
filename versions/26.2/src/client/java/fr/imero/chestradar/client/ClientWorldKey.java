package fr.imero.chestradar.client;

import fr.imero.chestradar.AeternumPaths;
import fr.imero.chestradar.data.WorldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.file.Path;

public final class ClientWorldKey {
	private ClientWorldKey() {
	}

	public static String current(Minecraft client) {
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
			return WorldKeys.forSingleplayer(levelName != null ? levelName : "sp-unknown-level");
		}
		ServerData entry = client.getCurrentServer();
		String address = entry != null ? entry.ip : "mp-unknown-server";
		return WorldKeys.forServer(address);
	}

	public static Path configFile() {
		return AeternumPaths.config("chestradar.json");
	}

	public static Path indexFile(Minecraft client) {
		return AeternumPaths.data("chestradar").resolve(current(client) + ".json");
	}
}
