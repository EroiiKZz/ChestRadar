package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.theme.AeternumTheme;

public final class RadarTheme {
	public static int ROW_EXPANDED;
	public static int PATINA;
	public static int PIN;
	public static int PROGRESS_TRACK;
	public static int PROGRESS_FILL;
	public static int PROGRESS_DONE;
	public static int MISSING;
	public static int OPEN_BUTTON;
	public static int OPEN_BUTTON_HOVER;
	public static int SLOT;
	public static int RAIL;
	public static int DIAL_FACE;

	public static final int ROW_HEIGHT = 20;
	public static final int SUB_ROW_HEIGHT = 18;
	public static final int CELL = 28;
	public static final int CELL_GAP = 4;
	public static final int PANEL_MIN_WIDTH = 200;
	public static final int PANEL_MIN_HEIGHT = 160;
	public static final int PANEL_MAX_WIDTH = 1600;
	public static final int PANEL_MAX_HEIGHT = 1000;
	public static final double PANEL_SIZE_FRACTION = 0.85;
	public static final int DIAL_SIZE = 25;
	public static final int SORT_BUTTON_WIDTH = 84;
	public static final int PIN_BUTTON_WIDTH = 18;

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
		int accent = palette.accent();
		int muted = palette.muted();
		int good = palette.good();

		ROW_EXPANDED = Theme.opaque(Theme.mix(background, surface, 0.55));
		PATINA = Theme.opaque(palette.secondary());
		PIN = Theme.opaque(Theme.mix(accent, 0xFFFFFF, 0.35));
		PROGRESS_TRACK = Theme.opaque(Theme.mix(background, border, 0.55));
		PROGRESS_FILL = Theme.opaque(good);
		PROGRESS_DONE = Theme.opaque(Theme.scale(good, 1.35));
		MISSING = Theme.opaque(palette.bad());
		OPEN_BUTTON = Theme.opaque(Theme.mix(surface, accent, 0.12));
		OPEN_BUTTON_HOVER = Theme.opaque(Theme.mix(surface, accent, 0.28));
		SLOT = Theme.opaque(Theme.mix(background, surface, 0.80));
		RAIL = Theme.opaque(Theme.mix(border, muted, 0.20));
		DIAL_FACE = Theme.opaque(Theme.mix(background, 0x000000, 0.10));
	}
}
