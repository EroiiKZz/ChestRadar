package fr.imero.chestradar.client;

import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.search.CollectionRoute;
import fr.imero.chestradar.search.Hit;
import fr.imero.chestradar.search.Pin;
import fr.imero.chestradar.search.RadarQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PinRoute {
	private PinRoute() {
	}

	public static boolean start(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			return false;
		}
		if (!RadarState.config().pinsEnabled) {
			warn(client, "chestradar.route.pins_off");
			return false;
		}
		List<Pin> targets = new ArrayList<>();
		for (Pin pin : RadarState.config().pins) {
			if (pin != null && pin.hasTarget()) {
				targets.add(pin);
			}
		}
		if (targets.isEmpty()) {
			warn(client, "chestradar.route.no_target");
			return false;
		}

		Map<String, Integer> carried = inventoryByFingerprint(client);
		String dimensionId = client.level.dimension().identifier().toString();
		var pos = client.player.position();
		double radius = RadarState.config().searchRadius;
		boolean allDimensions = RadarScope.allDimensions();
		var accept = RadarScope.accept();

		List<CollectionRoute.Need> needs = new ArrayList<>();
		List<CollectionRoute.Source> sources = new ArrayList<>();
		for (Pin pin : targets) {
			int missing = pin.target() - carried.getOrDefault(pin.fingerprint(), 0);
			if (missing <= 0) {
				continue;
			}
			String label = pin.displayName() == null || pin.displayName().isBlank()
				? pin.itemId() : pin.displayName();
			needs.add(new CollectionRoute.Need(pin.fingerprint(), label, missing));
			for (Hit hit : RadarQuery.findByFingerprint(RadarState.index(), dimensionId,
					pos.x, pos.y, pos.z, radius, pin.fingerprint(), allDimensions, accept)) {
				sources.add(new CollectionRoute.Source(hit.container().key(), hit.container().title(),
					pin.fingerprint(), hit.totalCount()));
			}
		}
		if (needs.isEmpty()) {
			warn(client, "chestradar.route.pins_done");
			return false;
		}

		CollectionRoute.Route route = CollectionRoute.plan(needs, sources, dimensionId,
			pos.x, pos.y, pos.z, RadarState.config().routeMaxStops);
		if (RadarRoute.start(client, route,
				Component.translatable("chestradar.route.goal_pins").getString())) {
			return true;
		}
		warn(client, "chestradar.route.none");
		return false;
	}

	private static Map<String, Integer> inventoryByFingerprint(Minecraft client) {
		Map<String, Integer> counts = new HashMap<>();
		var ops = ItemIdentityFactory.opsFor(client);
		if (ops == null) {
			return counts;
		}
		var rules = IdentityRules.from(RadarState.config());
		var inventory = client.player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			counts.merge(ItemIdentityFactory.from(stack, ops, rules).fingerprint(),
				ItemIdentityFactory.quantityOf(stack, rules), Integer::sum);
		}
		return counts;
	}

	private static void warn(Minecraft client, String key) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}
}
