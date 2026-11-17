package fr.imero.chestradar.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class CraftPlan {
	public static final int MAX_WANTED = 999_999;

	private CraftPlan() {
	}

	public record Slot(List<String> itemIds, String label) {
		public Slot {
			itemIds = List.copyOf(itemIds);
		}
	}

	public record Ingredient(List<String> itemIds, String label, int perCraft) {
		public Ingredient {
			itemIds = List.copyOf(itemIds);
		}

		public String key() {
			return keyOf(itemIds);
		}
	}

	public static String keyOf(java.util.Collection<String> itemIds) {
		return String.join("+", new TreeSet<>(itemIds));
	}

	public record Need(Ingredient ingredient, int needed, int inInventory, int inIndex) {
		public int missing() {
			return Math.max(0, needed - inInventory - inIndex);
		}

		public int toFetch() {
			return (int) Math.clamp((long) needed - inInventory, 0L, inIndex);
		}

		public boolean covered() {
			return missing() == 0;
		}

		public boolean inHand() {
			return inInventory >= needed;
		}
	}

	public record Plan(String outputLabel, int outputPerCraft, int wantedOutput, int crafts,
			List<Need> needs) {
		public Plan {
			needs = List.copyOf(needs);
		}

		public boolean complete() {
			return needs.stream().allMatch(Need::covered);
		}

		public boolean craftableNow() {
			return !needs.isEmpty() && needs.stream().allMatch(Need::inHand);
		}

		public int missingKinds() {
			return (int) needs.stream().filter(need -> !need.covered()).count();
		}

		public int producedOutput() {
			return crafts * outputPerCraft;
		}
	}

	public interface Supply {
		int inInventory(Ingredient ingredient);

		int inIndex(Ingredient ingredient);
	}

	public static int craftsFor(int wantedOutput, int outputPerCraft) {
		int wanted = Math.clamp(wantedOutput, 0, MAX_WANTED);
		int perCraft = Math.max(1, outputPerCraft);
		return (wanted + perCraft - 1) / perCraft;
	}

	public static List<Ingredient> aggregate(List<Slot> slots) {
		Map<String, Ingredient> byKey = new LinkedHashMap<>();
		if (slots == null) {
			return List.of();
		}
		for (Slot slot : slots) {
			if (slot == null || slot.itemIds().isEmpty()) {
				continue;
			}
			String key = keyOf(slot.itemIds());
			Ingredient existing = byKey.get(key);
			if (existing == null) {
				byKey.put(key, new Ingredient(slot.itemIds(),
					slot.label() == null ? "" : slot.label(), 1));
			} else {
				byKey.put(key, new Ingredient(existing.itemIds(), existing.label(),
					existing.perCraft() + 1));
			}
		}
		return new ArrayList<>(byKey.values());
	}

	public static Plan of(String outputLabel, int outputPerCraft, int wantedOutput,
			List<Slot> slots, Supply supply) {
		int crafts = craftsFor(wantedOutput, outputPerCraft);
		List<Need> needs = new ArrayList<>();
		for (Ingredient ingredient : aggregate(slots)) {
			int needed = ingredient.perCraft() * crafts;
			needs.add(new Need(ingredient, needed,
				Math.max(0, supply.inInventory(ingredient)),
				Math.max(0, supply.inIndex(ingredient))));
		}
		return new Plan(outputLabel == null ? "" : outputLabel, Math.max(1, outputPerCraft),
			Math.clamp(wantedOutput, 0, MAX_WANTED), crafts, needs);
	}
}
