package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.api.ChestRadarApi;
import fr.imero.chestradar.api.ChestRadarPlugin;
import fr.imero.chestradar.client.capture.ContainerCaptureHandler;
import fr.imero.chestradar.client.capture.InteractionTracker;
import fr.imero.chestradar.client.render.ChestHighlightRenderer;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.client.render.SlotHighlightRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ChestRadarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RadarState.reloadConfig();
		ChestRadarKeybinds.register();
		InteractionTracker.register();
		ContainerCaptureHandler.register();
		ContainerVerifier.register();
		ChestHighlightRenderer.register();
		SlotHighlightRenderer.register();
		RadarHud.register();
		InventorySearch.register();
		InventoryTotals.register();
		fr.imero.chestradar.client.craft.CraftPlanTab.register();
		loadPlugins();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RadarState.load(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			client.execute(() -> RadarState.unload(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			fr.imero.chestradar.client.screen.RadarTheme.sync();
			RadarActions.handleKeys(client);
			RadarState.tick(client);
			ContainerVerifier.tick(client);
			RadarTarget.tick(client);
			RadarRoute.tick(client);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ContainerCaptureHandler.flushActive();
			RadarState.retryPendingRescue();
			RadarState.saveNow(client);
		});

		ChestRadar.LOGGER.info("[{}] client init", ChestRadar.MOD_ID);
	}

	private static void loadPlugins() {
		for (var container : FabricLoader.getInstance().getEntrypointContainers(ChestRadarPlugin.ENTRYPOINT,
				ChestRadarPlugin.class)) {
			try {
				container.getEntrypoint().register(ChestRadarApi.get());
				ChestRadar.LOGGER.info("[chestradar] extension chargée : {}", container.getProvider().getMetadata().getId());
			} catch (RuntimeException | LinkageError e) {
				ChestRadar.LOGGER.warn("[chestradar] extension {} ignorée", container.getProvider().getMetadata().getId(), e);
			}
		}
	}
}
