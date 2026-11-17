package fr.imero.chestradar.data;

public record ContainerKey(String dimensionId, int x, int y, int z) {
	public double distanceTo(double px, double py, double pz) {
		double dx = x - px;
		double dy = y - py;
		double dz = z - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
