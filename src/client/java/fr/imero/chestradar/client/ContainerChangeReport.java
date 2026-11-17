package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.ContainerDiff;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class ContainerChangeReport {
	private ContainerChangeReport() {
	}

	public static void report(MinecraftClient client, ContainerRecord previous, ContainerRecord current) {
		if (!RadarState.config().reportContainerChanges || client == null || client.player == null) {
			return;
		}
		try {
			List<ContainerDiff.Change> changes = ContainerDiff.between(previous, current);
			if (changes.isEmpty()) {
				return;
			}
			client.player.sendMessage(Text.translatable("chestradar.changes.header",
				current.title()).formatted(Formatting.GOLD), false);

			int limit = Math.min(changes.size(), RadarState.config().containerChangeLimit);
			for (int i = 0; i < limit; i++) {
				client.player.sendMessage(line(changes.get(i)), false);
			}
			if (changes.size() > limit) {
				client.player.sendMessage(Text.translatable("chestradar.changes.more",
					changes.size() - limit).formatted(Formatting.DARK_GRAY), false);
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] journal des changements impossible", e);
		}
	}

	private static MutableText line(ContainerDiff.Change change) {
		int delta = change.delta();
		ItemIdentity identity = RadarState.index().pool().get(change.fingerprint());
		String name = identity != null ? identity.displayName() : change.fingerprint();
		String amount = String.format(Locale.ROOT, "%+,d", delta);
		return Text.translatable("chestradar.changes.line", amount, name, change.after())
			.formatted(delta > 0 ? Formatting.GREEN : Formatting.RED);
	}
}
