package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public final class ContainerVerifier {
	private static final int BATCH = 160;

	private static long lastRun;
	private static int cursor;

	private ContainerVerifier() {
	}

	public static void register() {
		ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
			try {
				onBroken(world, pos);
			} catch (Exception e) {
				ChestRadar.LOGGER.warn("[chestradar] retrait du conteneur casse impossible", e);
			}
		});
	}

	private static void onBroken(ClientLevel world, BlockPos pos) {
		if (!RadarState.config().autoRefreshIndex || world == null || pos == null) {
			return;
		}
		ContainerKey broken = new ContainerKey(world.dimension().identifier().toString(),
			pos.getX(), pos.getY(), pos.getZ());
		ContainerKey removed = RadarState.index().removeAt(broken);
		if (removed == null) {
			return;
		}
		ChestRadar.LOGGER.info("[chestradar] conteneur casse, retire de l'index : {} {} {} ({})",
			removed.x(), removed.y(), removed.z(), removed.dimensionId());
		afterRemoval(List.of(removed));
	}

	public static void tick(Minecraft client) {
		if (!RadarState.config().autoRefreshIndex || !RadarState.config().autoPurgeMissing
			|| client == null || client.level == null || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRun < RadarState.config().purgeCheckIntervalSeconds * 1000L) {
			return;
		}
		lastRun = now;
		try {
			sweep(client.level);
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] passe de verification des conteneurs interrompue", e);
		}
	}

	private static void sweep(ClientLevel world) {
		String dimensionId = world.dimension().identifier().toString();
		List<ContainerRecord> records = new ArrayList<>(RadarState.index().all());
		if (records.isEmpty()) {
			cursor = 0;
			return;
		}
		if (cursor >= records.size()) {
			cursor = 0;
		}

		List<ContainerKey> gone = new ArrayList<>();
		int examined = 0;
		int i = cursor;
		for (int scanned = 0; scanned < records.size() && examined < BATCH; scanned++, i++) {
			if (i >= records.size()) {
				i = 0;
			}
			ContainerRecord record = records.get(i);
			if (!record.key().dimensionId().equals(dimensionId)) {
				continue;
			}
			examined++;
			if (isMissing(world, record.key())) {
				gone.add(record.key());
			}
		}
		cursor = i;

		if (gone.isEmpty()) {
			return;
		}
		for (ContainerKey key : gone) {
			RadarState.index().remove(key);
			ChestRadar.LOGGER.info("[chestradar] conteneur disparu, retire de l'index : {} {} {} ({})",
				key.x(), key.y(), key.z(), key.dimensionId());
		}
		afterRemoval(gone);
	}

	private static void afterRemoval(List<ContainerKey> gone) {
		RadarState.index().pool().purgeOrphans(RadarState.index().all());
		RadarState.markDirty();
		RadarHud.invalidate();
		InventoryTotals.invalidate();
		RadarActions.dropHits(gone);
	}

	private static boolean isMissing(ClientLevel world, ContainerKey key) {
		BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
		if (!isLoaded(world, pos)) {
			return false;
		}
		Block block = world.getBlockState(pos).getBlock();
		return !(block instanceof ChestBlock || block instanceof BarrelBlock
			|| block instanceof ShulkerBoxBlock);
	}

	private static boolean isLoaded(ClientLevel world, BlockPos pos) {
		if (world.isOutsideBuildHeight(pos)) {
			return false;
		}
		return world.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
			ChunkStatus.FULL, false) != null;
	}
}
