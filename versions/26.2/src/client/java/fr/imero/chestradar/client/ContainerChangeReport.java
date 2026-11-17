package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.screen.RadarTheme;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.ContainerDiff;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Locale;

public final class ContainerChangeReport {
	private ContainerChangeReport() {
	}

	public static void report(Minecraft client, ContainerRecord previous, ContainerRecord current) {
		if (!RadarState.config().reportContainerChanges || client == null || client.player == null) {
			return;
		}
		try {
			List<ContainerDiff.Change> changes = ContainerDiff.between(previous, current);
			if (changes.isEmpty()) {
				return;
			}
			client.player.sendSystemMessage(Component.translatable("chestradar.changes.header",
				Component.literal(current.title()).withStyle(style -> style.withColor(Theme.rgb(Theme.ACCENT)))));

			int limit = Math.min(changes.size(), RadarState.config().containerChangeLimit);
			for (int i = 0; i < limit; i++) {
				client.player.sendSystemMessage(line(changes.get(i)));
			}
			if (changes.size() > limit) {
				client.player.sendSystemMessage(Component.translatable("chestradar.changes.more",
					changes.size() - limit).withStyle(ChatFormatting.DARK_GRAY));
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] journal des changements impossible", e);
		}
	}

	private static MutableComponent line(ContainerDiff.Change change) {
		int delta = change.delta();
		ItemIdentity identity = RadarState.index().pool().get(change.fingerprint());
		String name = identity != null ? identity.displayName() : change.fingerprint();
		String amount = String.format(Locale.ROOT, "%+,d", delta).replace('-', '−');
		int color = delta > 0 ? Theme.GOOD : Theme.mix(Theme.ALERT, 0xFFFFFFFF, 0.25);
		return Component.literal("    ")
			.append(Component.literal(amount).withStyle(style -> style.withColor(Theme.rgb(color))))
			.append(" " + name + " ")
			.append(Component.translatable("chestradar.changes.left", change.after()).withStyle(ChatFormatting.GRAY));
	}
}
