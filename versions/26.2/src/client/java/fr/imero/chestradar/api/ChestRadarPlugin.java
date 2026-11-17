package fr.imero.chestradar.api;

public interface ChestRadarPlugin {
	String ENTRYPOINT = "chestradar";

	void register(ChestRadarApi api);
}
