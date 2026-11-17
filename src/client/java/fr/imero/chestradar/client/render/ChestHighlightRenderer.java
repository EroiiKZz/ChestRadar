package fr.imero.chestradar.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.search.Hit;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.search.CompactCount;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ChestHighlightRenderer {
	private static final float RED = 0.88f;
	private static final float GREEN = 0.66f;
	private static final float BLUE = 0.29f;
	private static final float ALPHA = 1.0f;

	private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipeline.builder()
		.withLocation(Identifier.of(ChestRadar.MOD_ID, "pipeline/esp_lines"))
		.withVertexShader(Identifier.of("minecraft", "core/rendertype_lines"))
		.withFragmentShader(Identifier.of("minecraft", "core/rendertype_lines"))
		.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
		.withUniform("Projection", UniformType.UNIFORM_BUFFER)
		.withUniform("Fog", UniformType.UNIFORM_BUFFER)
		.withUniform("Globals", UniformType.UNIFORM_BUFFER)
		.withBlend(BlendFunction.TRANSLUCENT)
		.withCull(false)
		.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.build();

	private static final RenderLayer ESP_LINES_LAYER = RenderLayer.of(
		"chestradar_esp_lines",
		256,
		ESP_LINES_PIPELINE,
		RenderLayer.MultiPhaseParameters.builder().build(false));

	private static final float LABEL_SCALE = 0.025f;
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
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(ChestHighlightRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (!RadarState.config().highlightEnabled || RadarActions.lastHits().isEmpty()) {
			return;
		}
		long ttlMs = RadarState.config().highlightSeconds * 1000L;
		if (ttlMs <= 0 || System.currentTimeMillis() - searchTime > ttlMs) {
			return;
		}

		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}

		Vec3d cameraPos = context.worldState().cameraRenderState.pos;
		VertexConsumer buffer = consumers.getBuffer(ESP_LINES_LAYER);

		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		for (var hit : RadarActions.lastHits()) {
			drawBlockOutline(matrices, buffer, hit.container().key());
			ContainerKey secondary = hit.container().secondary();
			if (secondary != null) {
				drawBlockOutline(matrices, buffer, secondary);
			}
		}
		for (var hit : RadarActions.lastHits()) {
			drawLabel(matrices, consumers, hit, cameraPos);
		}
		matrices.pop();
	}

	private static void drawBlockOutline(MatrixStack matrices, VertexConsumer buffer, ContainerKey key) {
		Box box = new Box(key.x(), key.y(), key.z(), key.x() + 1.0, key.y() + 1.0, key.z() + 1.0);
		VertexRendering.drawBox(matrices.peek(), buffer, box, RED, GREEN, BLUE, ALPHA);
	}

	private static void drawLabel(MatrixStack matrices, VertexConsumerProvider consumers,
			Hit hit, Vec3d cameraPos) {
		if (!RadarState.config().highlightLabels) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null) {
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

		String label = hit.container().title() + "  x" + CompactCount.format(hit.totalCount());
		TextRenderer font = client.textRenderer;

		matrices.push();
		matrices.translate(centerX, centerY, centerZ);
		matrices.multiply(client.gameRenderer.getCamera().getRotation());
		matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

		float x = -font.getWidth(label) / 2.0f;
		font.draw(label, x, 0.0f, Theme.TEXT_PRIMARY, false,
			matrices.peek().getPositionMatrix(), consumers,
			TextRenderer.TextLayerType.SEE_THROUGH, LABEL_BACKGROUND,
			LightmapTextureManager.MAX_LIGHT_COORDINATE);
		matrices.pop();
	}
}
