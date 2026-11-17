package fr.imero.chestradar.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftPlanTest {
	private static CraftPlan.Slot slot(String id) {
		return new CraftPlan.Slot(List.of(id), id);
	}

	private static CraftPlan.Slot empty() {
		return new CraftPlan.Slot(List.of(), "");
	}

	private static CraftPlan.Supply supply(Map<String, Integer> inventory, Map<String, Integer> index) {
		return new CraftPlan.Supply() {
			@Override
			public int inInventory(CraftPlan.Ingredient ingredient) {
				return sum(inventory, ingredient);
			}

			@Override
			public int inIndex(CraftPlan.Ingredient ingredient) {
				return sum(index, ingredient);
			}

			private int sum(Map<String, Integer> stocks, CraftPlan.Ingredient ingredient) {
				int total = 0;
				for (String id : ingredient.itemIds()) {
					total += stocks.getOrDefault(id, 0);
				}
				return total;
			}
		};
	}

	@Test
	void aggregatesIdenticalSlotsAndSkipsEmptyOnes() {
		List<CraftPlan.Ingredient> ingredients = CraftPlan.aggregate(List.of(
			slot("minecraft:oak_planks"), slot("minecraft:oak_planks"), empty(),
			slot("minecraft:stick"), slot("minecraft:oak_planks")));

		assertEquals(2, ingredients.size());
		assertEquals("minecraft:oak_planks", ingredients.get(0).itemIds().get(0));
		assertEquals(3, ingredients.get(0).perCraft());
		assertEquals(1, ingredients.get(1).perCraft());
	}

	@Test
	void slotsWithTheSameCandidatesInAnotherOrderAreTheSameIngredient() {
		CraftPlan.Slot a = new CraftPlan.Slot(List.of("minecraft:oak_planks", "minecraft:birch_planks"), "Planches");
		CraftPlan.Slot b = new CraftPlan.Slot(List.of("minecraft:birch_planks", "minecraft:oak_planks"), "Planches");

		List<CraftPlan.Ingredient> ingredients = CraftPlan.aggregate(List.of(a, b));

		assertEquals(1, ingredients.size());
		assertEquals(2, ingredients.get(0).perCraft());
	}

	@Test
	void craftsAreRoundedUpNeverDown() {
		assertEquals(2, CraftPlan.craftsFor(5, 4));
		assertEquals(1, CraftPlan.craftsFor(4, 4));
		assertEquals(0, CraftPlan.craftsFor(0, 4));
		assertEquals(7, CraftPlan.craftsFor(7, 0));
	}

	@Test
	void wantedQuantityIsCappedSoNeedsCannotOverflow() {
		CraftPlan.Plan plan = CraftPlan.of("Bâton", 1, Integer.MAX_VALUE,
			List.of(slot("minecraft:oak_planks")), supply(Map.of(), Map.of()));

		assertEquals(CraftPlan.MAX_WANTED, plan.wantedOutput());
		assertTrue(plan.needs().get(0).needed() > 0, "un besoin négatif signalerait un débordement");
	}

	@Test
	void needsMultiplyByCraftsAndSplitInventoryFromContainers() {
		CraftPlan.Plan plan = CraftPlan.of("Rail", 16, 32, List.of(
				slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
				slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
				slot("minecraft:stick")),
			supply(Map.of("minecraft:iron_ingot", 4), Map.of("minecraft:iron_ingot", 20)));

		assertEquals(2, plan.crafts());
		assertEquals(32, plan.producedOutput());

		CraftPlan.Need iron = plan.needs().get(0);
		assertEquals(12, iron.needed());
		assertEquals(4, iron.inInventory());
		assertEquals(20, iron.inIndex());
		assertEquals(0, iron.missing());
		assertEquals(8, iron.toFetch());
		assertFalse(iron.inHand());

		CraftPlan.Need stick = plan.needs().get(1);
		assertEquals(2, stick.needed());
		assertEquals(2, stick.missing());
		assertEquals(0, stick.toFetch());
		assertFalse(plan.complete());
		assertEquals(1, plan.missingKinds());
	}

	@Test
	void toFetchNeverExceedsWhatContainersHold() {
		CraftPlan.Plan plan = CraftPlan.of("Rail", 1, 100, List.of(slot("minecraft:iron_ingot")),
			supply(Map.of(), Map.of("minecraft:iron_ingot", 30)));

		CraftPlan.Need iron = plan.needs().get(0);
		assertEquals(100, iron.needed());
		assertEquals(30, iron.toFetch());
		assertEquals(70, iron.missing());
	}

	@Test
	void everythingInInventoryMeansCraftableOnTheSpot() {
		CraftPlan.Plan plan = CraftPlan.of("Torche", 4, 4,
			List.of(slot("minecraft:coal"), slot("minecraft:stick")),
			supply(Map.of("minecraft:coal", 8, "minecraft:stick", 8), Map.of()));

		assertTrue(plan.complete());
		assertTrue(plan.craftableNow());
		assertEquals(0, plan.needs().get(0).toFetch());
	}

	@Test
	void severalCandidatesForOneSlotSumTheirStocks() {
		CraftPlan.Slot planks = new CraftPlan.Slot(
			List.of("minecraft:oak_planks", "minecraft:birch_planks"), "Planches");

		CraftPlan.Plan plan = CraftPlan.of("Établi", 1, 1, List.of(planks, planks, planks, planks),
			supply(Map.of("minecraft:birch_planks", 2), Map.of("minecraft:oak_planks", 2)));

		CraftPlan.Need need = plan.needs().get(0);
		assertEquals(4, need.needed());
		assertEquals(2, need.inInventory());
		assertEquals(2, need.inIndex());
		assertTrue(need.covered(), "deux essences différentes couvrent le même slot");
	}
}
