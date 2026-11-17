package fr.imero.chestradar.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.search.BulkQuantity;
import fr.imero.chestradar.search.Pin;
import fr.imero.chestradar.search.Pins;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ChestRadarConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final double PANEL_WIDTH_FRACTION_MIN = 0.40;
	public static final double PANEL_WIDTH_FRACTION_MAX = 1.00;

	public double searchRadius = 128.0;
	public boolean indexBarrels = true;
	public boolean indexShulkerBoxes = true;
	public boolean highlightEnabled = true;
	public int highlightSeconds = 12;
	public int maxIndexedContainers = 5000;
	@SerializedName(value = "sortMode", alternate = {"defaultSort"})
	public SortMode sortMode = SortMode.NAME;
	public int chatResultLimit = 8;
	public boolean searchIncludesLore = true;
	public List<String> ignoredComponentKeys = new ArrayList<>();

	public ViewMode viewMode = ViewMode.LIST;

	public boolean faceContainerOnLocate = true;

	public boolean debugCaptureDump = false;

	public boolean indexHopperGuiContainers = true;

	@SerializedName(value = "indexBulkQuantity", alternate = {"indexBulkQuantityFromLore"})
	public boolean indexBulkQuantity = true;

	public String bulkQuantityLorePattern = BulkQuantity.DEFAULT_PATTERN;

	public String bulkQuantityTitlePattern = BulkQuantity.DEFAULT_TITLE_PATTERN;

	public double panelWidthFraction = 0.85;

	public boolean indexNestedContents = true;

	public boolean autoRefreshIndex = true;

	public boolean showLastSeen = true;

	public int staleAfterDays = 7;

	public boolean hideStaleContainers = false;

	public boolean autoPurgeMissing = true;

	public int purgeCheckIntervalSeconds = 5;

	public boolean lookAtPreview = true;

	public int lookAtPreviewRows = 8;

	public boolean inventoryTotals = true;

	public boolean jadeIntegration = true;

	public boolean structuredSearch = true;

	public boolean searchAllDimensions = true;

	public boolean highlightLabels = true;

	public double highlightLabelDistance = 96.0;

	public boolean slotHighlightEnabled = true;

	public int slotHighlightSeconds = 10;

	public boolean compassEnabled = true;

	public double compassArrivalDistance = 4.0;

	public boolean reportContainerChanges = true;

	public boolean reportOnlyChangesBetweenVisits = true;

	public int containerChangeLimit = 6;

	public boolean pinsEnabled = true;

	public List<Pin> pins = new ArrayList<>();

	public boolean craftPlannerEnabled = true;

	public boolean craftPlannerTab = true;

	public boolean routeEnabled = true;

	public int routeMaxStops = 8;

	public void clamp() {
		searchRadius = Math.clamp(searchRadius, 16.0, 1024.0);
		highlightSeconds = Math.clamp(highlightSeconds, 0, 120);
		maxIndexedContainers = Math.clamp(maxIndexedContainers, 100, 100_000);
		chatResultLimit = Math.clamp(chatResultLimit, 1, 50);
		if (Double.isNaN(panelWidthFraction)) {
			panelWidthFraction = 0.85;
		}
		panelWidthFraction = Math.clamp(panelWidthFraction, PANEL_WIDTH_FRACTION_MIN, PANEL_WIDTH_FRACTION_MAX);
		if (sortMode == null) {
			sortMode = SortMode.NAME;
		}
		if (ignoredComponentKeys == null) {
			ignoredComponentKeys = new ArrayList<>();
		}
		if (viewMode == null) {
			viewMode = ViewMode.LIST;
		}
		if (bulkQuantityTitlePattern == null || bulkQuantityTitlePattern.isBlank()) {
			bulkQuantityTitlePattern = BulkQuantity.DEFAULT_TITLE_PATTERN;
		}
		if (bulkQuantityLorePattern == null || bulkQuantityLorePattern.isBlank()) {
			bulkQuantityLorePattern = BulkQuantity.DEFAULT_PATTERN;
		}

		staleAfterDays = Math.clamp(staleAfterDays, 0, 3650);
		purgeCheckIntervalSeconds = Math.clamp(purgeCheckIntervalSeconds, 1, 300);
		lookAtPreviewRows = Math.clamp(lookAtPreviewRows, 1, 20);		containerChangeLimit = Math.clamp(containerChangeLimit, 1, 50);
		if (Double.isNaN(highlightLabelDistance)) {
			highlightLabelDistance = 96.0;
		}
		highlightLabelDistance = Math.clamp(highlightLabelDistance, 8.0, 512.0);
		slotHighlightSeconds = Math.clamp(slotHighlightSeconds, 1, 60);
		if (Double.isNaN(compassArrivalDistance)) {
			compassArrivalDistance = 4.0;
		}
		compassArrivalDistance = Math.clamp(compassArrivalDistance, 1.0, 64.0);
		routeMaxStops = Math.clamp(routeMaxStops, 1, 24);
		pins = Pins.sanitize(pins);
	}

	public static ChestRadarConfig load(Path file) {
		ChestRadarConfig config = new ChestRadarConfig();
		if (Files.isRegularFile(file)) {
			try {
				ChestRadarConfig parsed = GSON.fromJson(Files.readString(file), ChestRadarConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			} catch (Exception e) {
				ChestRadar.LOGGER.warn("[chestradar] config illisible, retour aux valeurs par défaut", e);
			}
		}
		config.clamp();
		return config;
	}

	public void save(Path file) {
		try {
			clamp();
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			Files.writeString(file, GSON.toJson(this));
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] écriture de la config impossible", e);
		}
	}
}
