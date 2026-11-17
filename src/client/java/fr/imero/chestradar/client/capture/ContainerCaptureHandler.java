package fr.imero.chestradar.client.capture;

import com.google.gson.JsonElement;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ContainerChangeReport;
import fr.imero.chestradar.client.InventoryTotals;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.BulkQuantity;
import fr.imero.chestradar.search.StorageCell;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContainerCaptureHandler {
	private static final long INTERACTION_WINDOW_MS = 2000L;

	private static final Map<Screen, PendingCapture> ACTIVE = new IdentityHashMap<>();

	private ContainerCaptureHandler() {
	}

	private static final class PendingCapture {
		final ScreenHandler handler;
		final int slots;
		final ContainerResolver.Resolved resolved;
		final String title;
		final boolean hopperGui;
		boolean hasSnapshot;
		long lastContentHash;
		ContainerRecord latestSnapshot;
		ContainerRecord firstSnapshot;
		boolean committed;
		boolean snapshotFailureWarned;
		boolean debugDumped;

		PendingCapture(ScreenHandler handler, int slots, ContainerResolver.Resolved resolved, String title) {
			this.handler = handler;
			this.slots = slots;
			this.resolved = resolved;
			this.title = title;
			this.hopperGui = handler instanceof HopperScreenHandler;
		}
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			try {
				handleScreenInit(client, screen);
			} catch (Exception e) {
				ChestRadar.LOGGER.warn("[chestradar] initialisation de capture de conteneur impossible", e);
			}
		});
	}

	private static void handleScreenInit(MinecraftClient client, Screen screen) {
		if (!(screen instanceof HandledScreen<?> handled)) {
			return;
		}

		PendingCapture existing = ACTIVE.get(screen);
		if (existing != null) {
			attachListeners(client, screen, existing);
			return;
		}

		ScreenHandler handler = handled.getScreenHandler();
		int slots = containerSlotCount(handler);
		if (slots <= 0) {
			CaptureDebug.skipped("type de handler non gere", handler.getClass().getName());
			return;
		}
		BlockPos pos = InteractionTracker.recent(INTERACTION_WINDOW_MS);
		if (pos == null || client.world == null) {
			CaptureDebug.skipped("aucun bloc clique dans les 2s", pos);
			return;
		}
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		ContainerResolver.Resolved resolved = ContainerResolver.resolve(client.world, pos, dimensionId, slots);
		if (resolved == null || !kindEnabled(resolved.kind()) || !kindMatchesHandler(resolved.kind(), handler)) {
			CaptureDebug.skipped("bloc non resolu ou genre refuse",
				client.world.getBlockState(pos).getBlock() + " -> " + resolved);
			return;
		}

		InteractionTracker.consume();

		PendingCapture capture = new PendingCapture(handler, slots, resolved, screen.getTitle().getString());
		ACTIVE.put(screen, capture);
		attachListeners(client, screen, capture);
	}

	private static void attachListeners(MinecraftClient client, Screen screen, PendingCapture capture) {
		ScreenEvents.afterTick(screen).register(s -> updateSnapshot(client, capture));
		ScreenEvents.remove(screen).register(s -> commit(screen, capture));
	}

	private static boolean kindEnabled(ContainerKind kind) {
		return switch (kind) {
			case BARREL -> RadarState.config().indexBarrels;
			case SHULKER_BOX -> RadarState.config().indexShulkerBoxes;
			default -> true;
		};
	}

	private static boolean kindMatchesHandler(ContainerKind kind, ScreenHandler handler) {
		if (handler instanceof HopperScreenHandler) {
			return RadarState.config().indexHopperGuiContainers;
		}
		return switch (kind) {
			case SHULKER_BOX -> handler instanceof ShulkerBoxScreenHandler;
			case CHEST, BARREL -> handler instanceof GenericContainerScreenHandler;
			default -> false;
		};
	}

	private static int containerSlotCount(ScreenHandler handler) {
		if (handler instanceof GenericContainerScreenHandler generic) {
			return generic.getRows() * 9;
		}
		if (handler instanceof ShulkerBoxScreenHandler) {
			return ShulkerBoxBlockEntity.INVENTORY_SIZE;
		}
		if (handler instanceof HopperScreenHandler) {
			return RadarState.config().indexHopperGuiContainers ? HopperScreenHandler.SLOT_COUNT : 0;
		}
		return 0;
	}

	private static void updateSnapshot(MinecraftClient client, PendingCapture capture) {
		if (capture.committed) {
			return;
		}
		try {
			long fingerprint = contentFingerprint(capture.handler, capture.slots);
			if (capture.hasSnapshot && fingerprint == capture.lastContentHash) {
				return;
			}
			var ops = ItemIdentityFactory.opsFor(client);
			IdentityRules rules = IdentityRules.from(RadarState.config());
			int titleQuantity = capture.hopperGui
				? BulkQuantity.parse(List.of(capture.title),
					BulkQuantity.titlePatternFor(RadarState.config()))
				: 0;
			Map<String, Integer> counts = new LinkedHashMap<>();
			Map<String, Integer> nested = new LinkedHashMap<>();
			ItemStack sole = ItemStack.EMPTY;
			for (int i = 0; i < capture.slots; i++) {
				Slot slot = capture.handler.slots.get(i);
				ItemStack stack = slot.getStack();
				if (stack.isEmpty()) {
					continue;
				}
				if (sole.isEmpty()) {
					sole = stack;
				}
				ItemIdentity identity = ItemIdentityFactory.from(stack, ops, rules);
				RadarState.index().pool().intern(identity);
				counts.merge(identity.fingerprint(), ItemIdentityFactory.quantityOf(stack, rules),
					Integer::sum);

				if (RadarState.config().indexNestedContents) {
					for (ItemStack inner : NestedContents.contentsOf(stack)) {
						if (inner.isEmpty()) {
							continue;
						}
						ItemIdentity innerIdentity = ItemIdentityFactory.from(inner, ops, rules);
						RadarState.index().pool().intern(innerIdentity);
						nested.merge(innerIdentity.fingerprint(),
							ItemIdentityFactory.quantityOf(inner, rules), Integer::sum);
					}
				}
			}
			counts = StorageCell.collapse(counts, titleQuantity);
			counts = asCellContents(counts, sole, ops, rules, titleQuantity);
			capture.latestSnapshot = new ContainerRecord(capture.resolved.primary(), capture.resolved.secondary(),
				capture.resolved.kind(), capture.title, System.currentTimeMillis(), capture.slots, counts,
				nested);
			if (capture.firstSnapshot == null) {
				capture.firstSnapshot = capture.latestSnapshot;
			}
			capture.lastContentHash = fingerprint;
			capture.hasSnapshot = true;
			capture.snapshotFailureWarned = false;
			if (!capture.debugDumped) {
				capture.debugDumped = true;
				CaptureDebug.dump(capture.title, capture.handler, capture.slots,
					String.valueOf(capture.resolved.kind()));
			}
		} catch (Exception e) {
			if (!capture.snapshotFailureWarned) {
				capture.snapshotFailureWarned = true;
				ChestRadar.LOGGER.warn(
					"[chestradar] snapshot de conteneur impossible (un seul avertissement pour cette ouverture)", e);
			}
		}
	}

	private static Map<String, Integer> asCellContents(Map<String, Integer> counts, ItemStack sole,
			RegistryOps<JsonElement> ops, IdentityRules rules, int titleQuantity) {
		if (titleQuantity <= 0 || counts.size() != 1 || sole.isEmpty()) {
			return counts;
		}
		ItemIdentity plain = ItemIdentityFactory.from(sole, ops, rules, true);
		RadarState.index().pool().intern(plain);
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put(plain.fingerprint(), counts.values().iterator().next());
		return out;
	}

	private static long contentFingerprint(ScreenHandler handler, int slots) {
		long hash = 1125899906842597L;
		for (int i = 0; i < slots; i++) {
			ItemStack stack = handler.slots.get(i).getStack();
			int itemRawId;
			int count;
			int componentsHash;
			if (stack.isEmpty()) {
				itemRawId = -1;
				count = 0;
				componentsHash = 0;
			} else {
				itemRawId = Registries.ITEM.getRawId(stack.getItem());
				count = stack.getCount();
				componentsHash = stack.getComponentChanges().hashCode();
			}
			hash = hash * 31 + itemRawId;
			hash = hash * 31 + count;
			hash = hash * 31 + componentsHash;
		}
		return hash;
	}

	private static void commit(Screen screen, PendingCapture capture) {
		if (capture.committed) {
			return;
		}
		capture.committed = true;
		ACTIVE.remove(screen);
		ContainerRecord record = capture.latestSnapshot;
		if (record == null) {
			return;
		}
		try {
			ContainerRecord previous = RadarState.index().get(record.key());
			ContainerRecord compared = RadarState.config().reportOnlyChangesBetweenVisits
				&& capture.firstSnapshot != null ? capture.firstSnapshot : record;
			ContainerChangeReport.report(MinecraftClient.getInstance(), previous, compared);

			RadarState.index().upsert(record);
			RadarState.markDirty();
			RadarHud.invalidate();
			InventoryTotals.invalidate();
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] enregistrement du conteneur impossible", e);
		}
	}

	public static void flushActive() {
		for (Screen screen : new ArrayList<>(ACTIVE.keySet())) {
			PendingCapture capture = ACTIVE.get(screen);
			if (capture != null) {
				commit(screen, capture);
			}
		}
	}
}
