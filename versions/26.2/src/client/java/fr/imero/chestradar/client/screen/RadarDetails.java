package fr.imero.chestradar.client.screen;

import java.util.Locale;

final class RadarDetails {
	static String number(int value) {
		return String.format(Locale.FRANCE, "%,d", value).replace(' ', ' ').replace(' ', ' ');
	}
}
