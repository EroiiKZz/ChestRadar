package fr.imero.chestradar.client;

import fr.imero.chestradar.AeternumPaths;
import fr.imero.chestradar.data.WorldKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Path;

public final class ClientWorldKey {
	private ClientWorldKey() {
	}

	public static String current(MinecraftClient client) {
		if (client.isInSingleplayer() && client.getServer() != null) {
			String levelName = client.getServer().getSaveProperties().getLevelName();
			return WorldKeys.forSingleplayer(levelName != null ? levelName : "sp-unknown-level");
		}
		ServerInfo entry = client.getCurrentServerEntry();
		String address = entry != null ? entry.address : "mp-unknown-server";
		return WorldKeys.forServer(address);
	}

	public static Path configFile() {
		return AeternumPaths.config("chestradar.json");
	}

	public static Path indexFile(MinecraftClient client) {
		return AeternumPaths.data("chestradar").resolve(current(client) + ".json");
	}
}
