package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.client.screen.ChestRadarScreen;
import fr.imero.chestradar.client.screen.CraftPlanScreen;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.client.screen.RadarTheme;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.data.ItemIdentity;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class InventoryTotals {
	private record Owned(int count, int containers, double nearestDistance, ContainerKey nearest) {
	}

	private static final Owned NONE = new Owned(0, 0, 0, null);

	private static ItemStack memoStack;
	private static Owned memoOwned;

	private static boolean failureLogged;

	private InventoryTotals() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			try {
				append(stack, lines);
			} catch (Exception e) {
				if (!failureLogged) {
					failureLogged = true;
					ChestRadar.LOGGER.warn(
						"[chestradar] total impossible dans l'infobulle (un seul avertissement)", e);
				}
			}
		});
	}

	public static void invalidate() {
		memoStack = null;
		memoOwned = null;
	}

	private static void append(ItemStack stack, List<Text> lines) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!RadarState.config().inventoryTotals || client == null || client.player == null
			|| client.world == null || stack == null || stack.isEmpty()) {
			return;
		}
		if (client.currentScreen instanceof ChestRadarScreen
			|| client.currentScreen instanceof CraftPlanScreen) {
			return;
		}
		Owned owned = lookup(client, stack);
		if (owned.count() <= 0) {
			return;
		}

		lines.add(Text.translatable("chestradar.tooltip.owned", amount(owned.count(), Theme.ACCENT))
			.styled(style -> style.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		if (shiftDown(client)) {
			lines.add(detail(client, owned));
		}
	}

	private static boolean shiftDown(MinecraftClient client) {
		return InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static Text detail(MinecraftClient client, Owned owned) {
		String where = (int) owned.nearestDistance() + " m " + Bearing.toward(client, owned.nearest());
		return Text.translatable("chestradar.tooltip.owned_detail",
				Text.literal(Integer.toString(owned.containers())),
				Text.literal(where).styled(style -> style.withColor(Theme.rgb(RadarTheme.PATINA))))
			.styled(style -> style.withColor(Theme.rgb(Theme.TEXT_SECONDARY)));
	}

	private static Text amount(int count, int color) {
		return Text.literal(String.format(Locale.ROOT, "%,d", count))
			.styled(style -> style.withColor(Theme.rgb(color)));
	}

	private static Owned lookup(MinecraftClient client, ItemStack stack) {
		if (stack == memoStack && memoOwned != null) {
			return memoOwned;
		}
		var ops = ItemIdentityFactory.opsFor(client);
		if (ops == null) {
			return NONE;
		}
		ItemIdentity identity = ItemIdentityFactory.from(stack, ops,
			IdentityRules.from(RadarState.config()));
		Owned owned = compute(client, identity.fingerprint());
		memoStack = stack;
		memoOwned = owned;
		return owned;
	}

	private static Owned compute(MinecraftClient client, String fingerprint) {
		Predicate<ContainerRecord> accept = RadarScope.accept();
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		var pos = client.player.getEntityPos();

		int count = 0;
		int containers = 0;
		ContainerKey nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (ContainerRecord record : RadarState.index().all()) {
			if (!accept.test(record)) {
				continue;
			}
			int total = record.countOf(fingerprint) + record.nestedCountOf(fingerprint);
			if (total <= 0) {
				continue;
			}
			count += total;
			containers++;
			double distance = Dimensions.distance(record.key(), dimensionId, pos.x, pos.y, pos.z);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = record.key();
			}
		}
		return count <= 0 ? NONE : new Owned(count, containers, nearestDistance, nearest);
	}
}
