package fr.imero.chestradar.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChestRadarApi {
	private static final ChestRadarApi INSTANCE = new ChestRadarApi();

	private final List<ItemAnnotation> annotations = new CopyOnWriteArrayList<>();

	private ChestRadarApi() {
	}

	public static ChestRadarApi get() {
		return INSTANCE;
	}

	public void addItemAnnotation(ItemAnnotation annotation) {
		annotations.add(annotation);
	}

	public List<ItemAnnotation> itemAnnotations() {
		return List.copyOf(annotations);
	}
}
