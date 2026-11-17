package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.capture.ContainerCaptureHandler;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.capture.NestedContents;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.client.render.SlotHighlightRenderer;
import fr.imero.chestradar.config.ChestRadarConfig;
import fr.imero.chestradar.data.ContainerIndex;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.IndexStorage;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.data.ItemIdentityPool;
import fr.imero.chestradar.search.BulkQuantity;
import fr.imero.chestradar.search.StorageCell;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;

public final class RadarState {
	private static final long SAVE_DEBOUNCE_MS = 5000L;
	private static final long MAX_RETRY_INTERVAL_MS = 300_000L;

	private static ContainerIndex index = new ContainerIndex();
	private static ChestRadarConfig config = new ChestRadarConfig();
	private static boolean dirty;
	private static long lastSave;
	private static long retryIntervalMs = SAVE_DEBOUNCE_MS;
	private static boolean saveFailureWarned;

	private static Path rescuePath;
	private static ContainerIndex rescueIndex;
	private static long lastRescueAttempt;
	private static long rescueRetryIntervalMs = SAVE_DEBOUNCE_MS;

	private static Path sessionFile;

	private RadarState() {
	}

	public static ContainerIndex index() {
		return index;
	}

	public static ChestRadarConfig config() {
		return config;
	}

	public static void reloadConfig() {
		config = ChestRadarConfig.load(ClientWorldKey.configFile());
	}

	public static void load(MinecraftClient client) {
		ContainerCaptureHandler.flushActive();

		persistOutgoingSession(client);

		Path newSessionFile = ClientWorldKey.indexFile(client);
		ContainerIndex unwrittenRescue = reconcilePendingRescue(newSessionFile);

		index = IndexStorage.load(newSessionFile);
		sessionFile = newSessionFile;
		ItemIdentityRenderer.clearCache();
		dirty = mergeStorageCells(index);
		retryIntervalMs = SAVE_DEBOUNCE_MS;
		saveFailureWarned = false;

		if (unwrittenRescue != null) {
			dirty = true;
			mergeRescueInto(index, unwrittenRescue);
		}
	}

	private static boolean mergeStorageCells(ContainerIndex target) {
		int merged = StorageCell.merge(target, BulkQuantity.patternFor(config),
			BulkQuantity.titlePatternFor(config), NestedContents.volatileKeysFor(config));
		if (merged > 0) {
			ChestRadar.LOGGER.info(
				"[chestradar] {} entrees de cellule fusionnees avec l'item d'origine", merged);
		}
		return merged > 0;
	}

	private static void persistOutgoingSession(MinecraftClient client) {
		if (!dirty || sessionFile == null) {
			return;
		}
		ChestRadar.LOGGER.info(
			"[chestradar] nouvelle session sans deconnexion prealable : ecriture de l'index encore non sauvegarde de la session precedente ({})",
			sessionFile);
		saveNow(client);
		if (dirty) {
			stashRescue();
			dirty = false;
		}
	}

	private static ContainerIndex reconcilePendingRescue(Path newSessionFile) {
		if (rescueIndex == null || !samePath(rescuePath, newSessionFile)) {
			return null;
		}
		ContainerIndex pending = rescueIndex;
		Path pendingPath = rescuePath;
		clearRescue();

		pending.evictBeyond(config.maxIndexedContainers);
		if (IndexStorage.save(pendingPath, pending)) {
			ChestRadar.LOGGER.info(
				"[chestradar] sauvetage d'index ecrit a la reconnexion sur le meme monde : {}", pendingPath);
			return null;
		}
		ChestRadar.LOGGER.warn(
			"[chestradar] sauvetage d'index toujours impossible a la reconnexion, fusion dans l'index de session : {}",
			pendingPath);
		return pending;
	}

	private static void mergeRescueInto(ContainerIndex target, ContainerIndex rescue) {
		int merged = 0;
		try {
			for (ContainerRecord record : rescue.all()) {
				for (String fingerprint : record.allFingerprints()) {
					ItemIdentity identity = rescue.pool().get(fingerprint);
					if (identity != null) {
						target.pool().intern(identity);
					}
				}
				target.upsert(record);
				merged++;
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.warn(
				"[chestradar] fusion du sauvetage d'index interrompue apres {} conteneur(s)", merged, e);
			return;
		}
		ChestRadar.LOGGER.info("[chestradar] {} conteneur(s) du sauvetage fusionnes dans l'index de session", merged);
	}

	private static void clearRescue() {
		rescuePath = null;
		rescueIndex = null;
		rescueRetryIntervalMs = SAVE_DEBOUNCE_MS;
	}

	private static boolean samePath(Path a, Path b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		try {
			return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
		} catch (Exception e) {
			return false;
		}
	}

	public static void unload(MinecraftClient client) {
		ContainerCaptureHandler.flushActive();
		saveNow(client);
		if (dirty) {
			stashRescue();
			dirty = false;
		}
		index = new ContainerIndex();
		ItemIdentityRenderer.clearCache();
		RadarTarget.clear();
		RadarRoute.clear();
		SlotHighlightRenderer.clear();
		RadarHud.invalidate();
		InventoryTotals.invalidate();
		sessionFile = null;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void tick(MinecraftClient client) {
		if (dirty && System.currentTimeMillis() - lastSave > retryIntervalMs) {
			saveNow(client);
		}
		retryRescue(false);
	}

	public static void saveNow(MinecraftClient client) {
		if (!dirty) {
			return;
		}
		if (sessionFile == null) {
			return;
		}
		index.evictBeyond(config.maxIndexedContainers);
		boolean success = IndexStorage.save(sessionFile, index);
		lastSave = System.currentTimeMillis();
		if (success) {
			dirty = false;
			saveFailureWarned = false;
			retryIntervalMs = SAVE_DEBOUNCE_MS;
			return;
		}
		retryIntervalMs = Math.min(retryIntervalMs * 2, MAX_RETRY_INTERVAL_MS);
		if (!saveFailureWarned) {
			saveFailureWarned = true;
			warnSaveFailure(client);
		}
	}

	private static void stashRescue() {
		if (sessionFile == null) {
			ChestRadar.LOGGER.warn(
				"[chestradar] index non sauvegarde perdu : aucun chemin de session resolu pour un secours");
			return;
		}
		if (rescueIndex != null) {
			ChestRadar.LOGGER.warn(
				"[chestradar] sauvetage d'index precedent ({}) remplace avant d'avoir pu etre rejoue", rescuePath);
		}
		rescuePath = sessionFile;
		rescueIndex = index;
		rescueRetryIntervalMs = SAVE_DEBOUNCE_MS;
	}

	private static void retryRescue(boolean ignoreBackoff) {
		if (rescueIndex == null) {
			return;
		}
		if (!ignoreBackoff && System.currentTimeMillis() - lastRescueAttempt <= rescueRetryIntervalMs) {
			return;
		}
		lastRescueAttempt = System.currentTimeMillis();
		rescueIndex.evictBeyond(config.maxIndexedContainers);
		boolean success = IndexStorage.save(rescuePath, rescueIndex);
		if (success) {
			ChestRadar.LOGGER.info("[chestradar] sauvetage d'index reussi apres nouvelle tentative : {}", rescuePath);
			clearRescue();
			return;
		}
		rescueRetryIntervalMs = Math.min(rescueRetryIntervalMs * 2, MAX_RETRY_INTERVAL_MS);
	}

	public static void retryPendingRescue() {
		retryRescue(true);
	}

	private static void warnSaveFailure(MinecraftClient client) {
		if (client.player != null) {
			client.player.sendMessage(
				Text.translatable("chestradar.message.save_failed").formatted(Formatting.RED), false);
		}
		ChestRadar.LOGGER.warn("[chestradar] sauvegarde de l'index en echec, joueur averti (une seule fois)");
	}
}
