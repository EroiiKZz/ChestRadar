package fr.imero.chestradar.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import fr.imero.chestradar.ChestRadar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndexStorage {
	public static final int SCHEMA_VERSION = 2;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private IndexStorage() {
	}

	private static final class FileModel {
		@SerializedName("schemaVersion") int schemaVersion = SCHEMA_VERSION;
		@SerializedName("items") List<ItemModel> items = new ArrayList<>();
		@SerializedName("containers") List<ContainerModel> containers = new ArrayList<>();
	}

	private static final class ItemModel {
		String fingerprint;
		String itemId;
		String componentsJson;
		String displayName;
		String searchText;
		String loreText;
		List<String> enchantmentIds = new ArrayList<>();
	}

	private static final class ContainerModel {
		String dimension;
		int x;
		int y;
		int z;
		Integer sx;
		Integer sy;
		Integer sz;
		String kind;
		String title;
		long lastSeen;
		int slots;
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Integer> nestedCounts = new LinkedHashMap<>();
	}

	public static ContainerIndex load(Path file) {
		ContainerIndex index = new ContainerIndex();
		if (!Files.isRegularFile(file)) {
			return index;
		}
		FileModel model;
		try {
			model = GSON.fromJson(Files.readString(file), FileModel.class);
		} catch (Exception e) {
			quarantine(file, e);
			return index;
		}
		if (model == null || model.schemaVersion != SCHEMA_VERSION) {
			ChestRadar.LOGGER.warn("[chestradar] schema d'index {} inattendu (attendu {}), mise de cote : {}",
				model == null ? "absent" : String.valueOf(model.schemaVersion), SCHEMA_VERSION, file);
			quarantine(file, null);
			return index;
		}
		try {
			for (ItemModel item : model.items) {
				index.pool().intern(new ItemIdentity(item.itemId, item.componentsJson, item.fingerprint,
					item.displayName, item.searchText,
					item.loreText == null ? "" : item.loreText,
					item.enchantmentIds == null ? List.of() : item.enchantmentIds));
			}
			for (ContainerModel container : model.containers) {
				ContainerKey secondary = container.sx == null ? null
					: new ContainerKey(container.dimension, container.sx, container.sy, container.sz);
				index.upsert(new ContainerRecord(
					new ContainerKey(container.dimension, container.x, container.y, container.z),
					secondary, ContainerKind.valueOf(container.kind), container.title,
					container.lastSeen, container.slots, container.counts,
					container.nestedCounts == null ? Map.of() : container.nestedCounts));
			}
		} catch (Exception e) {
			quarantine(file, e);
			index.clear();
		}
		return index;
	}

	public static boolean save(Path file, ContainerIndex index) {
		Path tmp = null;
		try {
			index.pool().purgeOrphans(index.all());

			FileModel model = new FileModel();
			for (ItemIdentity identity : index.pool().all()) {
				ItemModel item = new ItemModel();
				item.fingerprint = identity.fingerprint();
				item.itemId = identity.itemId();
				item.componentsJson = identity.componentsJson();
				item.displayName = identity.displayName();
				item.searchText = identity.searchText();
				item.loreText = identity.loreText();
				item.enchantmentIds = new ArrayList<>(identity.enchantmentIds());
				model.items.add(item);
			}
			for (ContainerRecord record : index.all()) {
				ContainerModel container = new ContainerModel();
				container.dimension = record.key().dimensionId();
				container.x = record.key().x();
				container.y = record.key().y();
				container.z = record.key().z();
				if (record.secondary() != null) {
					container.sx = record.secondary().x();
					container.sy = record.secondary().y();
					container.sz = record.secondary().z();
				}
				container.kind = record.kind().name();
				container.title = record.title();
				container.lastSeen = record.lastSeenEpochMillis();
				container.slots = record.slotCount();
				container.counts = new LinkedHashMap<>(record.counts());
				container.nestedCounts = new LinkedHashMap<>(record.nestedCounts());
				model.containers.add(container);
			}

			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(model));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] sauvegarde de l'index impossible : {}", file, e);
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (Exception cleanupFailure) {
					ChestRadar.LOGGER.warn("[chestradar] nettoyage du fichier temporaire impossible : {}",
						tmp, cleanupFailure);
				}
			}
			return false;
		}
	}

	private static void quarantine(Path file, Exception cause) {
		if (cause != null) {
			ChestRadar.LOGGER.warn("[chestradar] index illisible, mis de cote en .corrupt : {}", file, cause);
		} else {
			ChestRadar.LOGGER.warn("[chestradar] index mis de cote en .corrupt : {}", file);
		}
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] mise de cote impossible", e);
		}
	}
}
