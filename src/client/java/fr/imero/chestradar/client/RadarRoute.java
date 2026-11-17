package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.search.CollectionRoute;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RadarRoute {
	private static List<CollectionRoute.Stop> stops = List.of();
	private static int index;
	private static String goal = "";

	private RadarRoute() {
	}

	public static boolean isActive() {
		return index < stops.size();
	}

	public static CollectionRoute.Stop currentStop() {
		return isActive() ? stops.get(index) : null;
	}

	public static int currentNumber() {
		return index + 1;
	}

	public static int size() {
		return stops.size();
	}

	public static String goal() {
		return goal;
	}

	public static boolean start(MinecraftClient client, CollectionRoute.Route route, String goalLabel) {
		clear();
		if (route == null || route.isEmpty()) {
			report(client, route);
			return false;
		}
		stops = List.copyOf(route.stops());
		index = 0;
		goal = goalLabel == null ? "" : goalLabel;

		int items = stops.stream().mapToInt(CollectionRoute.Stop::totalItems).sum();
		message(client, Text.translatable("chestradar.route.started", goal, stops.size(),
			CompactCount.format(items)).formatted(Formatting.GOLD));
		report(client, route);
		arm(client, true);
		return true;
	}

	public static void clear() {
		CollectionRoute.Stop current = currentStop();
		if (current != null && RadarTarget.isActive() && current.container().equals(RadarTarget.key())) {
			RadarTarget.clear();
		}
		stops = List.of();
		index = 0;
		goal = "";
	}

	public static void cancel(MinecraftClient client) {
		if (!isActive()) {
			return;
		}
		clear();
		message(client, Text.translatable("chestradar.route.cancelled").formatted(Formatting.GRAY));
	}

	public static void tick(MinecraftClient client) {
		if (!isActive() || client == null || client.player == null || client.world == null) {
			return;
		}
		CollectionRoute.Stop current = stops.get(index);
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		if (!dimensionId.equals(current.container().dimensionId())) {
			return;
		}
		var pos = client.player.getEntityPos();
		double distance = Dimensions.distance(current.container(), dimensionId, pos.x, pos.y, pos.z);
		if (distance <= RadarState.config().compassArrivalDistance) {
			message(client, Text.translatable("chestradar.route.arrived", currentNumber(), stops.size(),
				current.title()).formatted(Formatting.GREEN));
			index++;
			if (isActive()) {
				arm(client, true);
			} else {
				message(client, Text.translatable("chestradar.route.done", goal)
					.formatted(Formatting.GOLD));
				clear();
			}
			return;
		}
		if (!RadarTarget.isActive()) {
			arm(client, false);
		}
	}

	public static void dropStops(Collection<ContainerKey> removed) {
		if (!isActive() || removed.isEmpty()) {
			return;
		}
		boolean currentRemoved = removed.contains(stops.get(index).container());
		List<CollectionRoute.Stop> kept = new ArrayList<>();
		int newIndex = index;
		for (int i = 0; i < stops.size(); i++) {
			CollectionRoute.Stop stop = stops.get(i);
			if (removed.contains(stop.container())) {
				if (i < index) {
					newIndex--;
				}
				continue;
			}
			kept.add(stop);
		}
		stops = List.copyOf(kept);
		index = Math.max(0, newIndex);
		MinecraftClient client = MinecraftClient.getInstance();
		if (!isActive()) {
			clear();
			message(client, Text.translatable("chestradar.route.done", goal).formatted(Formatting.GRAY));
			return;
		}
		arm(client, currentRemoved);
	}

	private static void arm(MinecraftClient client, boolean announce) {
		CollectionRoute.Stop stop = currentStop();
		if (stop == null) {
			return;
		}
		RadarTarget.set(stop.container(), takesLabel(stop), stop.totalItems());
		ContainerRecord record = RadarState.index().at(stop.container());
		if (record != null) {
			RadarActions.highlight(List.of(new Hit(record, 0, stop.totalItems(), 0)));
		}
		if (announce && client != null) {
			message(client, Text.translatable("chestradar.route.stop", currentNumber(), stops.size(),
				stop.title(), takesLabel(stop)).formatted(Formatting.GOLD));
		}
	}

	public static String takesLabel(CollectionRoute.Stop stop) {
		if (stop == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (CollectionRoute.Take take : stop.takes()) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(CompactCount.format(take.quantity())).append(' ').append(take.label());
		}
		return sb.toString();
	}

	private static void report(MinecraftClient client, CollectionRoute.Route route) {
		if (route == null) {
			return;
		}
		for (CollectionRoute.Need need : route.unmet()) {
			message(client, Text.translatable("chestradar.route.unmet",
				CompactCount.format(need.quantity()), need.label()).formatted(Formatting.GRAY));
		}
		if (route.skippedSources() > 0) {
			message(client, Text.translatable("chestradar.route.other_dimension",
				route.skippedSources()).formatted(Formatting.DARK_GRAY));
		}
	}

	private static void message(MinecraftClient client, Text text) {
		if (client != null && client.player != null) {
			client.player.sendMessage(text, false);
		}
	}
}
