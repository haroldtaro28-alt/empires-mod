package com.empires;

/** تعريف الإمبراطورية: الاسم واللون وإحداثيات العاصمة (ثابتة لكل عالم). */
public record Empire(int id, String name, int color, int cx, int cz, int size) {
	public static final String[] NAMES = {
		"الإمبراطورية الرومانية", "الإمبراطورية الفارسية", "الإمبراطورية العثمانية", "الإمبراطورية المغولية",
		"الإمبراطورية البيزنطية", "الدولة الأموية", "الدولة العباسية", "الدولة الفاطمية", "الدولة الأيوبية",
		"دولة المماليك", "دولة المرابطين", "دولة الموحدين", "الدولة الغزنوية", "دولة السلاجقة", "الدولة الصفوية",
		"إمبراطورية المغول الهندية", "إمبراطورية الموريا", "الإمبراطورية الآشورية", "الإمبراطورية البابلية",
		"مملكة مصر الفرعونية", "الإمبراطورية الحثية", "قرطاج", "الإمبراطورية المقدونية", "الإمبراطورية الأخمينية",
		"الإمبراطورية الساسانية", "مملكة أكسوم", "إمبراطورية مالي", "إمبراطورية سونغاي", "مملكة غانا",
		"مملكة الزولو", "إمبراطورية الإنكا", "إمبراطورية الأزتك", "حضارة المايا", "إمبراطورية هان",
		"إمبراطورية تانغ", "إمبراطورية مينغ", "الإمبراطورية اليابانية", "إمبراطورية الخمير",
		"الإمبراطورية التيمورية", "الإمبراطورية الفرنسية", "الإمبراطورية البريطانية", "الإمبراطورية الإسبانية",
		"الإمبراطورية البرتغالية", "الإمبراطورية الروسية", "الإمبراطورية الألمانية", "مملكة السويد",
		"الفايكنغ", "مملكة الفرنجة", "الجمهورية الهولندية", "الإمبراطورية النمساوية"
	};
	public static final int COUNT = NAMES.length; // 50
	public static final Empire[] ALL = new Empire[COUNT];

	static {
		for (int i = 0; i < COUNT; i++) {
			// مواقع مؤقتة؛ المواقع الحقيقية العشوائية تُطبَّق عند بدء العالم (applyLayout)
			ALL[i] = new Empire(i, NAMES[i], hsv(i / (float) COUNT, 0.85F, 0.9F), 0, 0, 1000);
		}
	}

	/** layout = [x0, z0, size0, x1, z1, size1, ...] */
	public static void applyLayout(int[] l) {
		for (int i = 0; i < COUNT; i++) {
			ALL[i] = new Empire(i, NAMES[i], ALL[i].color(), l[i * 3], l[i * 3 + 1], l[i * 3 + 2]);
		}
	}

	public int radius() { return size / 2; }

	/** أي إمبراطورية تقع الإحداثيات داخل أراضيها (الأقرب نسبةً لحجمها)، أو null. */
	public static Empire territoryAt(double x, double z) {
		Empire best = null;
		double bs = 1.0;
		for (Empire e : ALL) {
			double dx = x - e.cx, dz = z - e.cz;
			double ratio = Math.sqrt(dx * dx + dz * dz) / (e.size / 2.0);
			if (ratio < bs) { bs = ratio; best = e; }
		}
		return best;
	}

	public static Empire get(int id) {
		return ALL[Math.floorMod(id, COUNT)];
	}

	private static int hsv(float h, float s, float v) {
		float r = 0, g = 0, b = 0;
		int i = (int) (h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
