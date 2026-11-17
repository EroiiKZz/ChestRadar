package fr.imero.chestradar.client.integration;

import fr.imero.arpenteur.api.ArpenteurApi;
import fr.imero.arpenteur.api.ArpenteurPlugin;
import fr.imero.arpenteur.api.MapMarker;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ArpenteurIntegration implements ArpenteurPlugin {
	private static final int CHEST = 0xC8913E;
	private static final int BARREL = 0x9C6B30;
	private static final int SHULKER = 0xB58BD6;
	private static final int OTHER = 0x9A9A9A;

	@Override
	public void register(ArpenteurApi api) {
		api.addMarkers(ArpenteurIntegration::markers);
	}

	private static List<MapMarker> markers(String dimension, int minX, int minZ, int maxX, int maxZ) {
		List<MapMarker> out = new ArrayList<>();
		for (ContainerRecord record : RadarState.index().all()) {
			ContainerKey key = record.key();
			if (!key.dimensionId().equals(dimension) || key.x() < minX || key.x() > maxX || key.z() < minZ
				|| key.z() > maxZ) {
				continue;
			}
			int items = 0;
			for (int count : record.counts().values()) {
				items += count;
			}
			out.add(new MapMarker(key.x(), key.y(), key.z(), color(record), Component.literal(record.title()),
				Component.translatable("chestradar.arpenteur.items", items)));
		}
		return out;
	}

	private static int color(ContainerRecord record) {
		return switch (record.kind()) {
			case CHEST -> CHEST;
			case BARREL -> BARREL;
			case SHULKER_BOX -> SHULKER;
			case OTHER -> OTHER;
		};
	}
}
