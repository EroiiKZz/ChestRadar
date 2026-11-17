package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ContainerKey;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ChestOpener {
	private ChestOpener() {
	}

	private static final double REACH_MARGIN = 0.0;

	public static boolean canOpen(MinecraftClient client, ContainerKey key) {
		if (client == null || client.player == null || client.world == null || key == null) {
			return false;
		}
		if (!client.world.getRegistryKey().getValue().toString().equals(key.dimensionId())) {
			return false;
		}
		BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
		if (!client.player.canInteractWithBlockAt(pos, REACH_MARGIN)) {
			return false;
		}
		Block block = client.world.getBlockState(pos).getBlock();
		return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock;
	}

	public static boolean open(MinecraftClient client, ContainerKey key) {
		if (!canOpen(client, key)) {
			return false;
		}
		BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
		Vec3d center = Vec3d.ofCenter(pos);
		Direction side = faceToward(center, client.player.getEyePos());
		Vec3d hitPos = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

		client.setScreen(null);
		if (client.interactionManager == null) {
			return false;
		}
		try {
			ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
				new BlockHitResult(hitPos, side, pos, false));
			if (result.isAccepted()) {
				client.player.swingHand(Hand.MAIN_HAND);
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] ouverture du conteneur en {} impossible", pos, e);
			return false;
		}
		return true;
	}

	private static Direction faceToward(Vec3d blockCenter, Vec3d eye) {
		double dx = eye.x - blockCenter.x;
		double dy = eye.y - blockCenter.y;
		double dz = eye.z - blockCenter.z;
		double ax = Math.abs(dx);
		double ay = Math.abs(dy);
		double az = Math.abs(dz);
		if (ay >= ax && ay >= az) {
			return dy >= 0 ? Direction.UP : Direction.DOWN;
		}
		if (ax >= az) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
