package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ContainerFocus {
	private ContainerFocus() {
	}

	public static void focus(Minecraft client, List<Hit> highlight, ContainerKey target) {
		focus(client, highlight, target, "", 0);
	}

	public static void focus(Minecraft client, List<Hit> highlight, ContainerKey target,
			String label, int quantity) {
		RadarActions.highlight(highlight);
		RadarTarget.set(target, label, quantity);
		if (client == null) {
			return;
		}
		client.setScreenAndShow(null);
		lookAt(client, target);
	}

	public static void lookAt(Minecraft client, ContainerKey target) {
		if (!RadarState.config().faceContainerOnLocate) {
			return;
		}
		if (client == null || client.player == null || target == null) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(new BlockPos(target.x(), target.y(), target.z()));
		client.player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
	}
}
