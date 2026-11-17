package fr.imero.chestradar.client.integration;

import fr.imero.chestradar.client.RadarState;
import net.fabricmc.loader.api.FabricLoader;

public final class JadeCompat {
	public static final String JADE_MOD_ID = "jade";

	private static Boolean installed;

	private JadeCompat() {
	}

	public static boolean installed() {
		if (installed == null) {
			installed = FabricLoader.getInstance().isModLoaded(JADE_MOD_ID);
		}
		return installed;
	}

	public static boolean handlesLookAtPreview() {
		return installed() && RadarState.config().jadeIntegration;
	}
}
