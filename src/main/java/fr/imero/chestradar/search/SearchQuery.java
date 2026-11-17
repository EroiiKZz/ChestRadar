package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.data.ItemIdentities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class SearchQuery {
	public record Context(boolean includeLore, long nowEpochMillis) {
	}

	private enum Field { TEXT, ENCH, ID, LORE, TITLE, KIND, DIM, DISTANCE, AGE_DAYS, COUNT_MIN, COUNT_MAX }

	private record Term(Field field, String value, double number, boolean negated) {
	}

	private static final Map<String, Field> PREFIXES = Map.ofEntries(
		Map.entry("ench", Field.ENCH),
		Map.entry("enchant", Field.ENCH),
		Map.entry("enchantement", Field.ENCH),
		Map.entry("id", Field.ID),
		Map.entry("lore", Field.LORE),
		Map.entry("desc", Field.LORE),
		Map.entry("in", Field.TITLE),
		Map.entry("dans", Field.TITLE),
		Map.entry("title", Field.TITLE),
		Map.entry("titre", Field.TITLE),
		Map.entry("kind", Field.KIND),
		Map.entry("type", Field.KIND),
		Map.entry("dim", Field.DIM),
		Map.entry("dimension", Field.DIM),
		Map.entry("m", Field.DISTANCE),
		Map.entry("dist", Field.DISTANCE),
		Map.entry("distance", Field.DISTANCE),
		Map.entry("d", Field.AGE_DAYS),
		Map.entry("j", Field.AGE_DAYS),
		Map.entry("age", Field.AGE_DAYS),
		Map.entry("days", Field.AGE_DAYS),
		Map.entry("jours", Field.AGE_DAYS));

	private static final SearchQuery EMPTY = new SearchQuery(List.of());

	private final List<Term> terms;

	private SearchQuery(List<Term> terms) {
		this.terms = terms;
	}

	public boolean isEmpty() {
		return terms.isEmpty();
	}

	public static SearchQuery parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return EMPTY;
		}
		List<Term> terms = new ArrayList<>();
		for (String token : tokenize(raw)) {
			Term term = toTerm(token);
			if (term != null) {
				terms.add(term);
			}
		}
		return terms.isEmpty() ? EMPTY : new SearchQuery(terms);
	}

	public static SearchQuery plain(String raw) {
		String needle = ItemIdentities.normalizeSearch(raw);
		return needle.isEmpty() ? EMPTY : new SearchQuery(List.of(new Term(Field.TEXT, needle, 0, false)));
	}

	private static List<String> tokenize(String raw) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '"') {
				quoted = !quoted;
				continue;
			}
			if (!quoted && Character.isWhitespace(c)) {
				if (!current.isEmpty()) {
					tokens.add(current.toString());
					current.setLength(0);
				}
				continue;
			}
			current.append(c);
		}
		if (!current.isEmpty()) {
			tokens.add(current.toString());
		}
		return tokens;
	}

	private static Term toTerm(String token) {
		boolean negated = token.length() > 1 && token.charAt(0) == '-';
		String body = negated ? token.substring(1) : token;
		if (body.isEmpty()) {
			return null;
		}

		if (body.charAt(0) == '>' || body.charAt(0) == '<') {
			double number = number(body.substring(1));
			if (Double.isNaN(number)) {
				return null;
			}
			Field field = body.charAt(0) == '>' ? Field.COUNT_MIN : Field.COUNT_MAX;
			return new Term(field, "", number, negated);
		}

		int colon = body.indexOf(':');
		if (colon > 0) {
			Field field = PREFIXES.get(body.substring(0, colon).toLowerCase(Locale.ROOT));
			if (field != null) {
				String value = body.substring(colon + 1);
				if (value.isBlank()) {
					return null;
				}
				if (field == Field.DISTANCE || field == Field.AGE_DAYS) {
					double number = number(value);
					return Double.isNaN(number) ? null : new Term(field, "", number, negated);
				}
				return new Term(field, ItemIdentities.normalizeSearch(value), 0, negated);
			}
		}

		String text = ItemIdentities.normalizeSearch(body);
		return text.isEmpty() ? null : new Term(Field.TEXT, text, 0, negated);
	}

	private static double number(String raw) {
		try {
			return Double.parseDouble(raw.replace(',', '.').trim());
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	public boolean matches(AggregatedItem item, Context context) {
		for (Term term : terms) {
			if (matchesTerm(item, term, context) == term.negated()) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesTerm(AggregatedItem item, Term term, Context context) {
		return switch (term.field()) {
			case TEXT -> item.identity().searchText().contains(term.value())
				|| (context.includeLore() && item.identity().loreText().contains(term.value()));
			case ENCH -> matchesEnchantment(item, term.value());
			case ID -> item.identity().itemId().toLowerCase(Locale.ROOT).contains(term.value());
			case LORE -> item.identity().loreText().contains(term.value());
			case COUNT_MIN -> item.totalCount() > term.number();
			case COUNT_MAX -> item.totalCount() < term.number();
			case TITLE -> anyHit(item, hit -> ItemIdentities.normalizeSearch(hit.container().title())
				.contains(term.value()));
			case KIND -> anyHit(item, hit -> matchesKind(hit.container().kind(), term.value()));
			case DIM -> anyHit(item, hit -> Dimensions.matches(hit.container().key().dimensionId(), term.value()));
			case DISTANCE -> anyHit(item, hit -> hit.distance() <= term.number());
			case AGE_DAYS -> anyHit(item, hit -> isRecent(hit.container(), term.number(), context.nowEpochMillis()));
		};
	}

	private static boolean matchesEnchantment(AggregatedItem item, String needle) {
		for (String enchantment : item.identity().enchantmentIds()) {
			if (ItemIdentities.normalizeSearch(enchantment).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesKind(ContainerKind kind, String needle) {
		String canonical = switch (kind) {
			case CHEST -> "chest coffre";
			case BARREL -> "barrel tonneau baril";
			case SHULKER_BOX -> "shulker";
			case OTHER -> "other autre";
		};
		return canonical.contains(needle);
	}

	private static boolean isRecent(ContainerRecord record, double days, long now) {
		return now - record.lastSeenEpochMillis() <= days * Freshness.DAY_MS;
	}

	private static boolean anyHit(AggregatedItem item, Predicate<Hit> predicate) {
		for (Hit hit : item.hits()) {
			if (predicate.test(hit)) {
				return true;
			}
		}
		return false;
	}
}
