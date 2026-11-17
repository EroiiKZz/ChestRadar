package fr.imero.chestradar.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.imero.chestradar.ChestRadar;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

final class IrisCompat {
	private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
	private static final String PROGRAM_CLASS = "net.irisshaders.iris.api.v0.IrisProgram";

	private IrisCompat() {
	}

	static void assignLinesProgram(RenderPipeline pipeline) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		try {
			Class<?> apiClass = Class.forName(API_CLASS);
			Class<?> programClass = Class.forName(PROGRAM_CLASS);
			Object api = apiClass.getMethod("getInstance").invoke(null);
			Object linesProgram = programClass.getField("LINES").get(null);
			Method assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
			assign.invoke(api, pipeline, linesProgram);
			ChestRadar.LOGGER.info("[{}] pipeline {} mappe sur le programme Iris LINES",
				ChestRadar.MOD_ID, pipeline.getLocation());
		} catch (ReflectiveOperationException | RuntimeException e) {
			ChestRadar.LOGGER.warn("[{}] Iris present mais pipeline non enregistre ({}), le surlignage peut rester invisible sous shaders",
				ChestRadar.MOD_ID, e.toString());
		}
	}
}
