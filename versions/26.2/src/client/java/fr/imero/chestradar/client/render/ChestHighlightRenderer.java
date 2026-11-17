package fr.imero.chestradar.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.Hit;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class ChestHighlightRenderer {
	private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "pipeline/esp_lines"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
		.build();

	private static final RenderType ESP_LINES = RenderType.create("chestradar_esp_lines",
		RenderSetup.builder(ESP_LINES_PIPELINE)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	private static final float OUTLINE_WIDTH = 2.0f;

	private static final int FULL_BRIGHT = 0xF000F0;

	private static final int LABEL_BACKGROUND = 0x80000000;

	private static long searchTime;

	private ChestHighlightRenderer() {
	}

	public static void onSearch() {
		searchTime = System.currentTimeMillis();
	}

	public static boolean isLive() {
		long ttlMs = RadarState.config().highlightSeconds * 1000L;
		return ttlMs > 0 && System.currentTimeMillis() - searchTime <= ttlMs;
	}

	public static boolean armedSince(long epochMillis) {
		return searchTime >= epochMillis;
	}

	public static void register() {
		IrisCompat.assignLinesProgram(ESP_LINES_PIPELINE);
		LevelRenderEvents.COLLECT_SUBMITS.register(ChestHighlightRenderer::collectSubmits);
	}

	private static void collectSubmits(LevelRenderContext context) {
		if (!RadarState.config().highlightEnabled || RadarActions.lastHits().isEmpty()) {
			return;
		}
		long ttlMs = RadarState.config().highlightSeconds * 1000L;
		if (ttlMs <= 0 || System.currentTimeMillis() - searchTime > ttlMs) {
			return;
		}

		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		if (pose == null || collector == null || context.levelState() == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null) {
			return;
		}
		Vec3 cameraPos = camera.pos;

		pose.pushPose();
		pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		for (var hit : RadarActions.lastHits()) {
			submitOutline(pose, collector, hit.container().key());
			ContainerKey secondary = hit.container().secondary();
			if (secondary != null) {
				submitOutline(pose, collector, secondary);
			}
		}
		for (var hit : RadarActions.lastHits()) {
			submitLabel(pose, collector, hit, cameraPos, camera);
		}
		pose.popPose();
	}

	private static void submitOutline(PoseStack pose, SubmitNodeCollector collector, ContainerKey key) {
		AABB box = new AABB(key.x(), key.y(), key.z(), key.x() + 1.0, key.y() + 1.0, key.z() + 1.0);
		collector.submitShapeOutline(pose, Shapes.create(box), ESP_LINES,
			fr.imero.chestradar.client.screen.Theme.ACCENT, OUTLINE_WIDTH, false);
	}

	private static void submitLabel(PoseStack pose, SubmitNodeCollector collector, Hit hit,
			Vec3 cameraPos, CameraRenderState camera) {
		if (!RadarState.config().highlightLabels) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.font == null) {
			return;
		}
		ContainerKey key = hit.container().key();
		double centerX = key.x() + 0.5;
		double centerY = key.y() + 1.35;
		double centerZ = key.z() + 0.5;
		double dx = centerX - cameraPos.x;
		double dy = centerY - cameraPos.y;
		double dz = centerZ - cameraPos.z;
		double limit = RadarState.config().highlightLabelDistance;
		if (dx * dx + dy * dy + dz * dz > limit * limit) {
			return;
		}

		Component label = Component.literal(hit.container().title()
			+ " ×" + CompactCount.format(hit.totalCount()) + "  ·  "
			+ (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + " "
			+ Component.translatable("chestradar.unit.blocks").getString());
		collector.submitNameTag(pose, new Vec3(centerX, centerY, centerZ), LABEL_BACKGROUND,
			label, true, FULL_BRIGHT, camera);
	}
}
