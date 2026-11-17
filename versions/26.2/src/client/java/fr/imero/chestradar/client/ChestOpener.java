package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ContainerKey;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class ChestOpener {
	private ChestOpener() {
	}

	private static final double REACH_MARGIN = 0.0;

	public static boolean canOpen(Minecraft client, ContainerKey key) {
		if (client == null || client.player == null || client.level == null || key == null) {
			return false;
		}
		if (!client.level.dimension().identifier().toString().equals(key.dimensionId())) {
			return false;
		}
		BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
		if (!client.player.isWithinBlockInteractionRange(pos, REACH_MARGIN)) {
			return false;
		}
		Block block = client.level.getBlockState(pos).getBlock();
		return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock;
	}

	public static boolean open(Minecraft client, ContainerKey key) {
		if (!canOpen(client, key)) {
			return false;
		}
		BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
		Vec3 center = Vec3.atCenterOf(pos);
		Direction side = faceToward(center, client.player.getEyePosition());
		Vec3 hitPos = center.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);

		client.setScreenAndShow(null);
		if (client.gameMode == null) {
			return false;
		}
		try {
			InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(hitPos, side, pos, false));
			if (result.consumesAction()) {
				client.player.swing(InteractionHand.MAIN_HAND);
			}
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] ouverture du conteneur en {} impossible", pos, e);
			return false;
		}
		return true;
	}

	private static Direction faceToward(Vec3 blockCenter, Vec3 eye) {
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
