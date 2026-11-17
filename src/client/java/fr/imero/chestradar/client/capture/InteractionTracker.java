package fr.imero.chestradar.client.capture;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class InteractionTracker {
	private static BlockPos lastPos;
	private static long lastTime;

	private InteractionTracker() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient()) {
				lastPos = hit.getBlockPos();
				lastTime = System.currentTimeMillis();
			}
			return ActionResult.PASS;
		});
	}

	public static BlockPos recent(long maxAgeMillis) {
		if (lastPos == null || System.currentTimeMillis() - lastTime > maxAgeMillis) {
			return null;
		}
		return lastPos;
	}

	public static void consume() {
		lastPos = null;
	}
}
