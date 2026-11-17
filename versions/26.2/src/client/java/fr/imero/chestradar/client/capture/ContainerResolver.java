package fr.imero.chestradar.client.capture;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKeys;
import fr.imero.chestradar.data.ContainerKind;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ContainerResolver {
	public record Resolved(ContainerKey primary, ContainerKey secondary, ContainerKind kind) {
	}

	private ContainerResolver() {
	}

	public static Resolved resolve(Level world, BlockPos pos, String dimensionId, int containerSlots) {
		BlockState state = world.getBlockState(pos);
		ContainerKey self = key(dimensionId, pos);

		if (isEnderChest(state.getBlock())) {
			return null;
		}
		if (state.getBlock() instanceof ChestBlock) {
			if (!state.hasProperty(ChestBlock.TYPE)) {
				return new Resolved(self, null, ContainerKind.CHEST);
			}
			ChestType type = state.getValue(ChestBlock.TYPE);
			if (type == ChestType.SINGLE || containerSlots <= 27) {
				return new Resolved(self, null, ContainerKind.CHEST);
			}
			BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
			ContainerKey other = key(dimensionId, otherPos);
			ContainerKey primary = ContainerKeys.primaryOf(self, other);
			ContainerKey secondary = primary.equals(self) ? other : self;
			return new Resolved(primary, secondary, ContainerKind.CHEST);
		}
		if (state.getBlock() instanceof BarrelBlock) {
			return new Resolved(self, null, ContainerKind.BARREL);
		}
		if (state.getBlock() instanceof ShulkerBoxBlock) {
			return new Resolved(self, null, ContainerKind.SHULKER_BOX);
		}
		return null;
	}

	private static boolean isEnderChest(Block block) {
		return block instanceof EnderChestBlock;
	}

	private static ContainerKey key(String dimensionId, BlockPos pos) {
		return new ContainerKey(dimensionId, pos.getX(), pos.getY(), pos.getZ());
	}
}
