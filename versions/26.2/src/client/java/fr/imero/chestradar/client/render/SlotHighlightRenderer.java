package fr.imero.chestradar.client.render;

import com.google.gson.JsonElement;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.client.capture.NestedContents;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.data.ItemIdentity;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class SlotHighlightRenderer {
	private static final long RESCAN_INTERVAL_MS = 250L;
	private static final int SLOT_SIZE = 16;
	private static final int FILL_ALPHA = 70;

	private static final Set<Screen> EQUIPPED = Collections.newSetFromMap(new WeakHashMap<>());

	private static String targetItemId;
	private static String targetFingerprint;

	private SlotHighlightRenderer() {
	}

	public static void arm(ItemIdentity identity, boolean wide) {
		if (identity == null) {
			return;
		}
		targetItemId = identity.itemId();
		targetFingerprint = wide ? null : identity.fingerprint();
	}

	public static void clear() {
		targetItemId = null;
		targetFingerprint = null;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> handled) || !EQUIPPED.add(screen)) {
				return;
			}
			long openedAt = System.currentTimeMillis();
			Scan scan = new Scan();
			ScreenEvents.afterExtract(screen).register((s, context, mouseX, mouseY, tickDelta) ->
				render(client, handled, context, openedAt, scan));
		});
	}

	private static final class Scan {
		long at;
		List<Slot> matched = List.of();
	}

	private static void render(Minecraft client, AbstractContainerScreen<?> screen, GuiGraphicsExtractor context,
			long openedAt, Scan scan) {
		if (targetItemId == null || !RadarState.config().slotHighlightEnabled) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - openedAt > RadarState.config().slotHighlightSeconds * 1000L) {
			return;
		}
		if (now - scan.at > RESCAN_INTERVAL_MS) {
			scan.at = now;
			scan.matched = matchedSlots(client, screen);
		}
		if (scan.matched.isEmpty()) {
			return;
		}
		int pulse = 165 + (int) Math.round(75.0 * Math.sin(now / 180.0));
		int fill = Theme.alpha(Theme.ACCENT, FILL_ALPHA);
		int border = Theme.alpha(Theme.ACCENT, pulse);
		for (Slot slot : scan.matched) {
			int sx = screen.leftPos + slot.x;
			int sy = screen.topPos + slot.y;
			context.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, fill);
			frame(context, sx - 1, sy - 1, SLOT_SIZE + 2, border);
		}
	}

	private static void frame(GuiGraphicsExtractor context, int x, int y, int size, int color) {
		context.fill(x, y, x + size, y + 1, color);
		context.fill(x, y + size - 1, x + size, y + size, color);
		context.fill(x, y + 1, x + 1, y + size - 1, color);
		context.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
	}

	private static List<Slot> matchedSlots(Minecraft client, AbstractContainerScreen<?> screen) {
		if (client == null || client.player == null) {
			return List.of();
		}
		RegistryOps<JsonElement> ops = ItemIdentityFactory.opsFor(client);
		if (ops == null) {
			return List.of();
		}
		IdentityRules rules = IdentityRules.from(RadarState.config());
		List<Slot> out = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == client.player.getInventory() || !slot.hasItem()) {
				continue;
			}
			if (matches(slot.getItem(), ops, rules)) {
				out.add(slot);
			}
		}
		return out;
	}

	private static boolean matches(ItemStack stack, RegistryOps<JsonElement> ops, IdentityRules rules) {
		if (directMatch(stack, ops, rules)) {
			return true;
		}
		if (!RadarState.config().indexNestedContents) {
			return false;
		}
		for (ItemStack nested : NestedContents.contentsOf(stack)) {
			if (directMatch(nested, ops, rules)) {
				return true;
			}
		}
		return false;
	}

	private static boolean directMatch(ItemStack stack, RegistryOps<JsonElement> ops, IdentityRules rules) {
		if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(targetItemId)) {
			return false;
		}
		if (targetFingerprint == null) {
			return true;
		}
		return targetFingerprint.equals(ItemIdentityFactory.from(stack, ops, rules).fingerprint());
	}
}
