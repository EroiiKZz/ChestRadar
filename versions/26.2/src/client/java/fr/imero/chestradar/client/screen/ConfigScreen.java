package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.ClientWorldKey;
import fr.imero.chestradar.client.InventoryTotals;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.RadarTarget;
import fr.imero.chestradar.client.integration.JadeCompat;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.config.ChestRadarConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public class ConfigScreen extends SettingsScreen {
	public ConfigScreen(Screen parent) {
		super(parent, Component.translatable("chestradar.config.title"), Items.COMPARATOR);
	}

	@Override
	protected void save() {
		RadarState.config().save(ClientWorldKey.configFile());
	}

	@Override
	protected void buildRows(int x, int w) {
		ChestRadarConfig config = RadarState.config();

		section("chestradar.config.section.search");
		slider(x, w, "chestradar.config.radius", 16, 1024,
			() -> config.searchRadius, v -> config.searchRadius = v, v -> unit(v, "chestradar.unit.blocks"));
		toggle(x, w, "chestradar.config.lore", config.searchIncludesLore,
			v -> config.searchIncludesLore = v);
		toggle(x, w, "chestradar.config.structured_search", config.structuredSearch,
			v -> config.structuredSearch = v);
		toggle(x, w, "chestradar.config.all_dimensions", config.searchAllDimensions,
			v -> config.searchAllDimensions = v);
		slider(x, w, "chestradar.config.chat_limit", 1, 50,
			() -> config.chatResultLimit, v -> config.chatResultLimit = (int) v, String::valueOf);

		section("chestradar.config.section.freshness");
		toggle(x, w, "chestradar.config.auto_refresh", config.autoRefreshIndex,
			v -> config.autoRefreshIndex = v);
		toggle(x, w, "chestradar.config.show_last_seen", config.showLastSeen,
			v -> config.showLastSeen = v);
		slider(x, w, "chestradar.config.stale_after", 0, 365,
			() -> config.staleAfterDays, v -> config.staleAfterDays = (int) v,
			v -> v == 0 ? I18n.get("chestradar.config.never") : unit(v, "chestradar.unit.days"))
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex);
		toggle(x, w, "chestradar.config.hide_stale", config.hideStaleContainers,
			v -> config.hideStaleContainers = v)
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex)
			.requires("chestradar.config.stale_after", () -> config.staleAfterDays > 0);
		toggle(x, w, "chestradar.config.auto_purge", config.autoPurgeMissing,
			v -> config.autoPurgeMissing = v)
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex);
		slider(x, w, "chestradar.config.purge_interval", 1, 60,
			() -> config.purgeCheckIntervalSeconds, v -> config.purgeCheckIntervalSeconds = (int) v,
			v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex)
			.requires("chestradar.config.auto_purge", () -> config.autoPurgeMissing);

		section("chestradar.config.section.world");
		toggle(x, w, "chestradar.config.highlight", config.highlightEnabled,
			v -> config.highlightEnabled = v);
		slider(x, w, "chestradar.config.highlight_seconds", 0, 120,
			() -> config.highlightSeconds, v -> config.highlightSeconds = (int) v, v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.highlight", () -> config.highlightEnabled);
		toggle(x, w, "chestradar.config.highlight_labels", config.highlightLabels,
			v -> config.highlightLabels = v)
			.requires("chestradar.config.highlight", () -> config.highlightEnabled);
		slider(x, w, "chestradar.config.label_distance", 8, 512,
			() -> config.highlightLabelDistance, v -> config.highlightLabelDistance = v, v -> unit(v, "chestradar.unit.blocks"))
			.requires("chestradar.config.highlight", () -> config.highlightEnabled)
			.requires("chestradar.config.highlight_labels", () -> config.highlightLabels);
		toggle(x, w, "chestradar.config.slot_highlight", config.slotHighlightEnabled,
			v -> config.slotHighlightEnabled = v);
		slider(x, w, "chestradar.config.slot_highlight_seconds", 1, 60,
			() -> config.slotHighlightSeconds, v -> config.slotHighlightSeconds = (int) v,
			v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.slot_highlight", () -> config.slotHighlightEnabled);
		toggle(x, w, "chestradar.config.compass", config.compassEnabled,
			v -> config.compassEnabled = v);
		slider(x, w, "chestradar.config.arrival_distance", 1, 64,
			() -> config.compassArrivalDistance, v -> config.compassArrivalDistance = v, v -> unit(v, "chestradar.unit.blocks"))
			.requires("chestradar.config.compass", () -> config.compassEnabled);
		toggle(x, w, "chestradar.config.face_container", config.faceContainerOnLocate,
			v -> config.faceContainerOnLocate = v);
		toggle(x, w, "chestradar.config.look_at_preview", config.lookAtPreview,
			v -> config.lookAtPreview = v);
		slider(x, w, "chestradar.config.preview_rows", 1, 20,
			() -> config.lookAtPreviewRows, v -> config.lookAtPreviewRows = (int) v, String::valueOf)
			.requires("chestradar.config.look_at_preview", () -> config.lookAtPreview);
		toggle(x, w, "chestradar.config.inventory_totals", config.inventoryTotals,
			v -> config.inventoryTotals = v);
		toggle(x, w, "chestradar.config.jade", config.jadeIntegration,
			v -> config.jadeIntegration = v)
			.dependsOn(() -> config.lookAtPreview && JadeCompat.installed())
			.disabledSays("chestradar.config.jade.requires");

		section("chestradar.config.section.capture");
		toggle(x, w, "chestradar.config.barrels", config.indexBarrels,
			v -> config.indexBarrels = v);
		toggle(x, w, "chestradar.config.shulkers", config.indexShulkerBoxes,
			v -> config.indexShulkerBoxes = v);
		toggle(x, w, "chestradar.config.hopper_gui", config.indexHopperGuiContainers,
			v -> config.indexHopperGuiContainers = v);
		toggle(x, w, "chestradar.config.bulk_quantity", config.indexBulkQuantity,
			v -> config.indexBulkQuantity = v);
		toggle(x, w, "chestradar.config.nested", config.indexNestedContents,
			v -> config.indexNestedContents = v);

		section("chestradar.config.section.changes");
		toggle(x, w, "chestradar.config.report_changes", config.reportContainerChanges,
			v -> config.reportContainerChanges = v);
		toggle(x, w, "chestradar.config.between_visits", config.reportOnlyChangesBetweenVisits,
			v -> config.reportOnlyChangesBetweenVisits = v)
			.requires("chestradar.config.report_changes", () -> config.reportContainerChanges);
		slider(x, w, "chestradar.config.change_limit", 1, 50,
			() -> config.containerChangeLimit, v -> config.containerChangeLimit = (int) v,
			String::valueOf)
			.requires("chestradar.config.report_changes", () -> config.reportContainerChanges);

		section("chestradar.config.section.interface");
		toggle(x, w, "chestradar.config.pins", config.pinsEnabled,
			v -> config.pinsEnabled = v);
		slider(x, w, "chestradar.config.panel_width",
			(int) Math.round(ChestRadarConfig.PANEL_WIDTH_FRACTION_MIN * 100),
			(int) Math.round(ChestRadarConfig.PANEL_WIDTH_FRACTION_MAX * 100),
			() -> config.panelWidthFraction * 100, v -> config.panelWidthFraction = v / 100.0,
			v -> v + " %");

		section("chestradar.config.section.craft");
		toggle(x, w, "chestradar.config.craft_planner", config.craftPlannerEnabled,
			v -> config.craftPlannerEnabled = v);
		toggle(x, w, "chestradar.config.craft_tab", config.craftPlannerTab,
			v -> config.craftPlannerTab = v)
			.requires("chestradar.config.craft_planner", () -> config.craftPlannerEnabled);
		toggle(x, w, "chestradar.config.route", config.routeEnabled,
			v -> config.routeEnabled = v);
		slider(x, w, "chestradar.config.route_stops", 1, 24,
			() -> config.routeMaxStops, v -> config.routeMaxStops = (int) v, String::valueOf)
			.requires("chestradar.config.route", () -> config.routeEnabled);

		section("chestradar.config.section.data");
		action(x, w, "chestradar.config.clear", () -> {
			RadarState.index().clear();
			RadarActions.clearHits();
			RadarTarget.clear();
			RadarRoute.clear();
			RadarHud.invalidate();
			InventoryTotals.invalidate();
			RadarState.markDirty();
			RadarState.saveNow(minecraft);
		}).dependsOn(() -> minecraft != null && minecraft.level != null);
	}}
