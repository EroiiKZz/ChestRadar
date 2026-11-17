package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class ContainerFocus {
	private ContainerFocus() {
	}

	public static void focus(MinecraftClient client, List<Hit> highlight, ContainerKey target) {
		focus(client, highlight, target, "", 0);
	}

	public static void focus(MinecraftClient client, List<Hit> highlight, ContainerKey target,
			String label, int quantity) {
		RadarActions.highlight(highlight);
		RadarTarget.set(target, label, quantity);
		if (client == null) {
			return;
		}
		client.setScreen(null);
		lookAt(client, target);
	}

	public static void lookAt(MinecraftClient client, ContainerKey target) {
		if (!RadarState.config().faceContainerOnLocate) {
			return;
		}
		if (client == null || client.player == null || target == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(new BlockPos(target.x(), target.y(), target.z()));
		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, center);
	}
}
