package fr.imero.chestradar.client.craft;

import fr.imero.chestradar.client.RadarScope;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.search.CollectionRoute;
import fr.imero.chestradar.search.CraftPlan;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftPlanner {
	private CraftPlanner() {
	}

	public record Planned(RecipeCatalog.Entry entry, CraftPlan.Plan plan,
			Map<String, List<Hit>> hits) {
		public Planned {
			hits = Map.copyOf(hits);
		}

		public List<Hit> hitsOf(CraftPlan.Ingredient ingredient) {
			return hits.getOrDefault(ingredient.key(), List.of());
		}

		public Hit nearest(CraftPlan.Ingredient ingredient) {
			List<Hit> list = hitsOf(ingredient);
			return list.isEmpty() ? null : list.get(0);
		}
	}

	public static Planned plan(Minecraft client, RecipeCatalog.Entry entry, int wantedOutput) {
		Map<String, List<Hit>> hits = new LinkedHashMap<>();
		if (client == null || client.player == null || client.level == null || entry == null) {
			return new Planned(entry, new CraftPlan.Plan("", 1, 0, 0, List.of()), hits);
		}
		String dimensionId = client.level.dimension().identifier().toString();
		Vec3 pos = client.player.position();
		double radius = RadarState.config().searchRadius;
		boolean allDimensions = RadarScope.allDimensions();
		var accept = RadarScope.accept();

		CraftPlan.Plan plan = CraftPlan.of(entry.label(), entry.outputPerCraft(), wantedOutput,
			entry.slots(), new CraftPlan.Supply() {
				@Override
				public int inInventory(CraftPlan.Ingredient ingredient) {
					return countInInventory(client, new HashSet<>(ingredient.itemIds()));
				}

				@Override
				public int inIndex(CraftPlan.Ingredient ingredient) {
					List<Hit> found = fr.imero.chestradar.search.RadarQuery.findByItemIds(
						RadarState.index(), dimensionId, pos.x, pos.y, pos.z, radius,
						new HashSet<>(ingredient.itemIds()), allDimensions, accept);
					hits.put(ingredient.key(), found);
					return found.stream().mapToInt(Hit::totalCount).sum();
				}
			});
		return new Planned(entry, plan, hits);
	}

	public static CollectionRoute.Route route(Minecraft client, Planned planned) {
		List<CollectionRoute.Need> needs = new ArrayList<>();
		List<CollectionRoute.Source> sources = new ArrayList<>();
		if (client == null || client.player == null || client.level == null || planned == null) {
			return new CollectionRoute.Route(List.of(), List.of(), 0);
		}
		for (CraftPlan.Need need : planned.plan().needs()) {
			int toFetch = need.toFetch();
			if (toFetch <= 0) {
				continue;
			}
			String key = need.ingredient().key();
			needs.add(new CollectionRoute.Need(key, need.ingredient().label(), toFetch));
			for (Hit hit : planned.hitsOf(need.ingredient())) {
				sources.add(new CollectionRoute.Source(hit.container().key(), hit.container().title(),
					key, hit.totalCount()));
			}
		}
		String dimensionId = client.level.dimension().identifier().toString();
		Vec3 pos = client.player.position();
		return CollectionRoute.plan(needs, sources, dimensionId, pos.x, pos.y, pos.z,
			RadarState.config().routeMaxStops);
	}

	public static int countInInventory(Minecraft client, Set<String> itemIds) {
		if (client == null || client.player == null || itemIds.isEmpty()) {
			return 0;
		}
		int total = 0;
		var rules = IdentityRules.from(RadarState.config());
		var inventory = client.player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				total += ItemIdentityFactory.quantityOf(stack, rules);
			}
		}
		return total;
	}
}
