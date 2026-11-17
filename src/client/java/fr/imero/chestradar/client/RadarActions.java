package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.client.capture.NestedContents;
import fr.imero.chestradar.client.render.ChestHighlightRenderer;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.client.render.SlotHighlightRenderer;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.client.screen.ChestRadarScreen;
import fr.imero.chestradar.client.screen.ConfigScreen;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.Hit;
import fr.imero.chestradar.search.RadarQuery;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class RadarActions {
	private static List<Hit> lastHits = new ArrayList<>();

	private RadarActions() {
	}

	public static List<Hit> lastHits() {
		return lastHits;
	}

	public static void clearHits() {
		lastHits = new ArrayList<>();
		SlotHighlightRenderer.clear();
	}

	public static void dropHits(java.util.Collection<fr.imero.chestradar.data.ContainerKey> removed) {
		if (removed.isEmpty() || lastHits.isEmpty()) {
			return;
		}
		List<Hit> kept = new ArrayList<>();
		for (Hit hit : lastHits) {
			if (!removed.contains(hit.container().key())) {
				kept.add(hit);
			}
		}
		lastHits = kept;
		if (RadarTarget.isActive() && removed.contains(RadarTarget.key())) {
			RadarTarget.clear();
		}
		RadarRoute.dropStops(removed);
	}

	public static void highlight(List<Hit> hits) {
		lastHits = new ArrayList<>(hits);
		ChestHighlightRenderer.onSearch();
	}

	public static void handleKeys(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		while (ChestRadarKeybinds.SEARCH.wasPressed()) {
			searchHeldItem(client);
		}
		while (ChestRadarKeybinds.OPEN_RADAR.wasPressed()) {
			client.setScreen(new ChestRadarScreen());
		}
		while (ChestRadarKeybinds.OPEN_CONFIG.wasPressed()) {
			client.setScreen(new ConfigScreen(null));
		}
		while (ChestRadarKeybinds.TOGGLE_HIGHLIGHT.wasPressed()) {
			RadarState.config().highlightEnabled = !RadarState.config().highlightEnabled;
			RadarState.config().save(ClientWorldKey.configFile());
		}
		while (ChestRadarKeybinds.OPEN_CRAFT_PLAN.wasPressed()) {
			if (RadarState.config().craftPlannerEnabled) {
				client.setScreen(new fr.imero.chestradar.client.screen.CraftPlanScreen(null));
			}
		}
		while (ChestRadarKeybinds.CANCEL_ROUTE.wasPressed()) {
			RadarRoute.cancel(client);
		}
	}

	public static void searchHeldItem(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ItemStack held = client.player.getMainHandStack();
		if (held.isEmpty()) {
			client.player.sendMessage(Text.translatable("chestradar.message.no_item")
				.formatted(Formatting.GRAY), false);
			return;
		}
		search(client, held);
	}

	public static void search(MinecraftClient client, ItemStack stack) {
		if (client.player == null || client.world == null || stack == null || stack.isEmpty()) {
			return;
		}

		var ops = ItemIdentityFactory.opsFor(client);
		if (ops == null) {
			ChestRadar.LOGGER.warn("[chestradar] recherche impossible : aucun gestionnaire reseau actif");
			return;
		}
		ItemIdentity identity = ItemIdentityFactory.from(stack, ops,
			IdentityRules.from(RadarState.config()));
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		double radius = RadarState.config().searchRadius;
		Vec3d pos = client.player.getEntityPos();

		boolean wide = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
		SlotHighlightRenderer.arm(identity, wide);
		boolean allDimensions = RadarScope.allDimensions();
		var accept = RadarScope.accept();
		List<Hit> hits = wide
			? RadarQuery.findByItemId(RadarState.index(), dimensionId, pos.x, pos.y, pos.z, radius,
				identity.itemId(), allDimensions, accept)
			: RadarQuery.findByFingerprint(RadarState.index(), dimensionId, pos.x, pos.y, pos.z, radius,
				identity.fingerprint(), allDimensions, accept);

		lastHits = hits;
		ChestHighlightRenderer.onSearch();

		if (hits.isEmpty()) {
			client.player.sendMessage(Text.translatable("chestradar.message.none",
				stack.getName(), (int) radius).formatted(Formatting.GRAY), false);
			return;
		}

		ContainerFocus.lookAt(client, hits.get(0).container().key());
		RadarTarget.set(hits.get(0).container().key(), stack.getName().getString(),
			hits.get(0).totalCount());

		client.player.sendMessage(Text.translatable("chestradar.message.header",
			stack.getName(), hits.size(), (int) radius).formatted(Formatting.GOLD), false);

		int limit = Math.min(hits.size(), RadarState.config().chatResultLimit);
		for (int i = 0; i < limit; i++) {
			Hit hit = hits.get(i);
			var line = Text.translatable("chestradar.message.line",
				hit.container().title(), hit.totalCount(),
				hit.container().key().x(), hit.container().key().y(), hit.container().key().z(),
				(int) hit.distance());
			if (hit.nestedCount() > 0) {
				line.append(Text.translatable("chestradar.message.nested", hit.nestedCount()));
			}
			if (!dimensionId.equals(hit.container().key().dimensionId())) {
				line.append(Text.translatable("chestradar.message.dimension",
					Dimensions.shortName(hit.container().key().dimensionId())));
			}
			if (RadarState.config().showLastSeen) {
				line.append(Text.literal("  ")).append(RadarHud.ageText(hit.container())
					.copy().formatted(RadarScope.isStale(hit.container())
						? Formatting.DARK_RED : Formatting.DARK_GRAY));
			}
			client.player.sendMessage(line.formatted(Formatting.GRAY), false);
		}
	}
}
