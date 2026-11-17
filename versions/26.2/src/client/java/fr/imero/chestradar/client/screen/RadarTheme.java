package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.theme.AeternumTheme;

public final class RadarTheme {
	public static int ROW_EXPANDED;
	public static int PATINA;
	public static int PROGRESS_TRACK;
	public static int PROGRESS_DONE;
	public static int RAIL;

	public static final int ROW_HEIGHT = Theme.ROW;
	public static final int SUB_ROW_HEIGHT = Theme.ROW_DENSE;
	public static final double PANEL_SIZE_FRACTION = 0.85;

	private static int appliedRevision = Integer.MIN_VALUE;

	static {
		apply(AeternumTheme.Preset.DEFAULT.palette());
	}

	private RadarTheme() {
	}

	public static void sync() {
		Theme.sync();
		int revision = AeternumTheme.revision();
		if (revision != appliedRevision) {
			appliedRevision = revision;
			apply(AeternumTheme.palette());
		}
	}

	private static void apply(AeternumTheme.Palette palette) {
		int background = palette.background();
		int surface = palette.surface();
		int border = palette.border();

		ROW_EXPANDED = Theme.opaque(Theme.mix(background, surface, 0.55));
		PATINA = Theme.opaque(palette.secondary());
		PROGRESS_TRACK = Theme.opaque(Theme.mix(background, border, 0.55));
		PROGRESS_DONE = Theme.opaque(Theme.scale(palette.good(), 1.35));
		RAIL = Theme.opaque(Theme.mix(border, palette.muted(), 0.20));
	}
}
