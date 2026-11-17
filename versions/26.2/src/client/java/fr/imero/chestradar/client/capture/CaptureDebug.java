package fr.imero.chestradar.client.capture;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.RadarState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;

public final class CaptureDebug {
	private static final int MAX_COMPONENT_CHARS = 400;

	private CaptureDebug() {
	}

	public static boolean enabled() {
		try {
			return RadarState.config().debugCaptureDump;
		} catch (Exception e) {
			return false;
		}
	}

	public static void skipped(String reason, Object detail) {
		if (!enabled()) {
			return;
		}
		ChestRadar.LOGGER.info("[chestradar][debug] ecran non capture ({}) : {}", reason, detail);
	}

	public static void dump(String title, AbstractContainerMenu handler, int slots, String kind) {
		if (!enabled()) {
			return;
		}
		try {
			ChestRadar.LOGGER.info("[chestradar][debug] ---- capture ----");
			ChestRadar.LOGGER.info("[chestradar][debug] titre GUI   : \"{}\"", title);
			ChestRadar.LOGGER.info("[chestradar][debug] handler     : {}", handler.getClass().getName());
			ChestRadar.LOGGER.info("[chestradar][debug] genre / slots: {} / {}", kind, slots);
			for (int i = 0; i < slots && i < handler.slots.size(); i++) {
				dumpSlot(i, handler.slots.get(i).getItem());
			}
			ChestRadar.LOGGER.info("[chestradar][debug] ---- fin ----");
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar][debug] vidage de diagnostic interrompu", e);
		}
	}

	private static void dumpSlot(int index, ItemStack stack) {
		if (stack.isEmpty()) {
			ChestRadar.LOGGER.info("[chestradar][debug]   slot {} : (vide)", index);
			return;
		}
		ChestRadar.LOGGER.info("[chestradar][debug]   slot {} : {} x{}  nom=\"{}\"", index,
			BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), stack.getHoverName().getString());
		var lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			int line = 0;
			for (Component text : lore.lines()) {
				ChestRadar.LOGGER.info("[chestradar][debug]       lore[{}] = \"{}\"", line++, text.getString());
			}
		}
		String components = String.valueOf(stack.getComponentsPatch());
		if (components.length() > MAX_COMPONENT_CHARS) {
			components = components.substring(0, MAX_COMPONENT_CHARS) + "… (tronque)";
		}
		ChestRadar.LOGGER.info("[chestradar][debug]       composants = {}", components);
	}
}
