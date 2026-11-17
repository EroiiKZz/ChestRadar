package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.search.AggregatedItem;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public interface RadarView {
	void setItems(List<AggregatedItem> items);

	void resetScroll();

	void render(DrawContext context, int mouseX, int mouseY);

	boolean mouseClicked(double mouseX, double mouseY, int button);

	boolean mouseScrolled(double mouseX, double mouseY, double amount);

	AggregatedItem hoveredItem(double mouseX, double mouseY);

	void select(String fingerprint);

	boolean clearSelection();
}
