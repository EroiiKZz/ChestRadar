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
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
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

	public static void handleKeys(Minecraft client) {
		if (client.player == null) {
			return;
		}
		while (ChestRadarKeybinds.SEARCH.consumeClick()) {
			searchHeldItem(client);
		}
		while (ChestRadarKeybinds.OPEN_RADAR.consumeClick()) {
			client.setScreenAndShow(new ChestRadarScreen());
		}
		while (ChestRadarKeybinds.OPEN_CONFIG.consumeClick()) {
			client.setScreenAndShow(new ConfigScreen(null));
		}
		while (ChestRadarKeybinds.TOGGLE_HIGHLIGHT.consumeClick()) {
			RadarState.config().highlightEnabled = !RadarState.config().highlightEnabled;
			RadarState.config().save(ClientWorldKey.configFile());
		}
		while (ChestRadarKeybinds.OPEN_CRAFT_PLAN.consumeClick()) {
			if (RadarState.config().craftPlannerEnabled) {
				client.setScreenAndShow(new fr.imero.chestradar.client.screen.CraftPlanScreen(null));
			}
		}
		while (ChestRadarKeybinds.CANCEL_ROUTE.consumeClick()) {
			RadarRoute.cancel(client);
		}
	}

	public static void searchHeldItem(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		ItemStack held = client.player.getMainHandItem();
		if (held.isEmpty()) {
			client.player.sendSystemMessage(Component.translatable("chestradar.message.no_item")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		search(client, held);
	}

	public static void search(Minecraft client, ItemStack stack) {
		if (client.player == null || client.level == null || stack == null || stack.isEmpty()) {
			return;
		}

		var ops = ItemIdentityFactory.opsFor(client);
		if (ops == null) {
			ChestRadar.LOGGER.warn("[chestradar] recherche impossible : aucun gestionnaire reseau actif");
			return;
		}
		ItemIdentity identity = ItemIdentityFactory.from(stack, ops,
			IdentityRules.from(RadarState.config()));
		String dimensionId = client.level.dimension().identifier().toString();
		double radius = RadarState.config().searchRadius;
		Vec3 pos = client.player.position();

		boolean wide = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
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
			client.player.sendSystemMessage(Component.translatable("chestradar.message.none",
				stack.getHoverName(), (int) radius).withStyle(ChatFormatting.GRAY));
			return;
		}

		ContainerFocus.lookAt(client, hits.get(0).container().key());
		RadarTarget.set(hits.get(0).container().key(), stack.getHoverName().getString(),
			hits.get(0).totalCount());

		client.player.sendSystemMessage(Component.translatable("chestradar.message.header",
			stack.getHoverName(), hits.size(), (int) radius).withStyle(ChatFormatting.GOLD));

		int limit = Math.min(hits.size(), RadarState.config().chatResultLimit);
		for (int i = 0; i < limit; i++) {
			Hit hit = hits.get(i);
			var line = Component.translatable("chestradar.message.line",
				hit.container().title(), hit.totalCount(),
				hit.container().key().x(), hit.container().key().y(), hit.container().key().z(),
				(int) hit.distance());
			if (hit.nestedCount() > 0) {
				line.append(Component.translatable("chestradar.message.nested", hit.nestedCount()));
			}
			if (!dimensionId.equals(hit.container().key().dimensionId())) {
				line.append(Component.translatable("chestradar.message.dimension",
					Dimensions.shortName(hit.container().key().dimensionId())));
			}
			if (RadarState.config().showLastSeen) {
				line.append(Component.literal("  ")).append(RadarHud.ageText(hit.container())
					.copy().withStyle(RadarScope.isStale(hit.container())
						? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY));
			}
			client.player.sendSystemMessage(line.withStyle(ChatFormatting.GRAY));
		}
	}
}
