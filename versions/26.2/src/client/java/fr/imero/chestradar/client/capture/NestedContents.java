package fr.imero.chestradar.client.capture;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.config.ChestRadarConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NestedContents {
	private static final String CONTAINER_KEY = "minecraft:container";
	private static final String BUNDLE_KEY = "minecraft:bundle_contents";

	private NestedContents() {
	}

	public static Set<String> volatileKeysFor(ChestRadarConfig config) {
		Set<String> keys = new HashSet<>(config.ignoredComponentKeys);
		if (config.indexNestedContents) {
			keys.add(CONTAINER_KEY);
			keys.add(BUNDLE_KEY);
		}
		return keys;
	}

	public static List<ItemStack> contentsOf(ItemStack stack) {
		List<ItemStack> out = new ArrayList<>();
		try {
			var container = stack.get(DataComponents.CONTAINER);
			if (container != null) {
				container.allItemsCopyStream().filter(s -> !s.isEmpty()).forEach(out::add);
			}
			var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				bundle.itemCopyStream().forEach(out::add);
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] lecture du contenu imbrique impossible pour {}", stack, e);
		}
		return out;
	}
}
