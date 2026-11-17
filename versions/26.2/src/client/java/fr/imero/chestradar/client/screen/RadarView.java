package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.search.AggregatedItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public interface RadarView {
	void setItems(List<AggregatedItem> items);

	void resetScroll();

	void render(GuiGraphicsExtractor context, int mouseX, int mouseY);

	boolean mouseClicked(double mouseX, double mouseY, int button);

	boolean mouseScrolled(double mouseX, double mouseY, double amount);

	AggregatedItem hoveredItem(double mouseX, double mouseY);

	void select(String fingerprint);

	boolean clearSelection();

	default AggregatedItem selectedItem() {
		return null;
	}
}
