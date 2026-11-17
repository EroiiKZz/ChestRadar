package fr.imero.chestradar.client;

import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Pin;
import fr.imero.chestradar.search.Pins;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class RadarPins {
	private RadarPins() {
	}

	public static boolean isPinned(String fingerprint) {
		return RadarState.config().pinsEnabled && Pins.isPinned(RadarState.config().pins, fingerprint);
	}

	public static Pin find(String fingerprint) {
		return RadarState.config().pinsEnabled ? Pins.find(RadarState.config().pins, fingerprint) : null;
	}

	public static boolean toggle(AggregatedItem item) {
		if (!RadarState.config().pinsEnabled || item == null) {
			return false;
		}
		String fingerprint = item.identity().fingerprint();
		boolean wasPinned = Pins.isPinned(RadarState.config().pins, fingerprint);
		RadarState.config().pins = Pins.toggle(RadarState.config().pins,
			new Pin(fingerprint, item.identity().itemId(), item.identity().displayName(), 0));
		boolean isPinned = Pins.isPinned(RadarState.config().pins, fingerprint);

		if (wasPinned == isPinned) {
			warn("chestradar.pin.full", Pins.MAX);
			return false;
		}
		save();
		return true;
	}

	public static void setTarget(String fingerprint, int target) {
		RadarState.config().pins = Pins.withTarget(RadarState.config().pins, fingerprint, target);
		save();
	}

	private static void save() {
		RadarState.config().save(ClientWorldKey.configFile());
	}

	private static void warn(String key, Object... args) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.player != null) {
			client.player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
		}
	}
}
