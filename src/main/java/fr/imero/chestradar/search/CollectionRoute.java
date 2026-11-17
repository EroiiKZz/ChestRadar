package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CollectionRoute {
	public record Need(String key, String label, int quantity) {
	}

	public record Source(ContainerKey container, String title, String needKey, int available) {
	}

	public record Take(String needKey, String label, int quantity) {
	}

	public record Stop(ContainerKey container, String title, List<Take> takes) {
		public Stop {
			takes = List.copyOf(takes);
		}

		public int totalItems() {
			return takes.stream().mapToInt(Take::quantity).sum();
		}
	}

	public record Route(List<Stop> stops, List<Need> unmet, int skippedSources) {
		public Route {
			stops = List.copyOf(stops);
			unmet = List.copyOf(unmet);
		}

		public boolean isEmpty() {
			return stops.isEmpty();
		}

		public int size() {
			return stops.size();
		}
	}

	private CollectionRoute() {
	}

	public static Route plan(List<Need> needs, List<Source> sources, String dimensionId,
			double px, double py, double pz, int maxStops) {
		Map<String, Integer> remaining = new LinkedHashMap<>();
		Map<String, String> labels = new LinkedHashMap<>();
		if (needs != null) {
			for (Need need : needs) {
				if (need == null || need.key() == null || need.quantity() <= 0) {
					continue;
				}
				remaining.merge(need.key(), need.quantity(), Integer::sum);
				labels.putIfAbsent(need.key(), need.label() == null ? need.key() : need.label());
			}
		}

		Map<ContainerKey, Candidate> candidates = new LinkedHashMap<>();
		int skipped = 0;
		if (sources != null) {
			for (Source source : sources) {
				if (source == null || source.container() == null || source.available() <= 0
					|| source.needKey() == null || !remaining.containsKey(source.needKey())) {
					continue;
				}
				if (!source.container().dimensionId().equals(dimensionId)) {
					skipped++;
					continue;
				}
				candidates.computeIfAbsent(source.container(),
						key -> new Candidate(key, source.title() == null ? "" : source.title()))
					.stock.merge(source.needKey(), source.available(), Integer::sum);
			}
		}

		List<Stop> stops = new ArrayList<>();
		double cx = px;
		double cy = py;
		double cz = pz;
		int limit = Math.max(1, maxStops);

		while (!remaining.isEmpty() && stops.size() < limit) {
			Candidate best = null;
			double bestDistance = Double.MAX_VALUE;
			for (Candidate candidate : candidates.values()) {
				if (candidate.visited || !candidate.serves(remaining)) {
					continue;
				}
				double distance = candidate.key.distanceTo(cx, cy, cz);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
			if (best == null) {
				break;
			}
			best.visited = true;

			List<Take> takes = new ArrayList<>();
			for (Map.Entry<String, Integer> need : new ArrayList<>(remaining.entrySet())) {
				int here = best.stock.getOrDefault(need.getKey(), 0);
				if (here <= 0) {
					continue;
				}
				int take = Math.min(need.getValue(), here);
				takes.add(new Take(need.getKey(), labels.get(need.getKey()), take));
				if (take >= need.getValue()) {
					remaining.remove(need.getKey());
				} else {
					remaining.put(need.getKey(), need.getValue() - take);
				}
			}
			stops.add(new Stop(best.key, best.title, takes));
			cx = best.key.x() + 0.5;
			cy = best.key.y() + 0.5;
			cz = best.key.z() + 0.5;
		}

		List<Need> unmet = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
			unmet.add(new Need(entry.getKey(), labels.get(entry.getKey()), entry.getValue()));
		}
		return new Route(stops, unmet, skipped);
	}

	private static final class Candidate {
		private final ContainerKey key;
		private final String title;
		private final Map<String, Integer> stock = new LinkedHashMap<>();
		private boolean visited;

		private Candidate(ContainerKey key, String title) {
			this.key = key;
			this.title = title;
		}

		private boolean serves(Map<String, Integer> remaining) {
			for (String needKey : remaining.keySet()) {
				if (stock.getOrDefault(needKey, 0) > 0) {
					return true;
				}
			}
			return false;
		}
	}
}
