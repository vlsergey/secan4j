package io.github.vlsergey.secan4j.core.colorless;

import java.util.Random;

public class SimpleMethods {

	public String simpleReturn(String src) {
		final String result = src;
		return result;
	}

	public int sumThree(int a, int b, int c) {
		return a + b + c;
	}

	public static String xOrNull(Object x) {
		return x != null ? x.toString() : "null";
	}

	public String whileWithRandom() {
		String result = "";
		while (new Random().nextBoolean()) {
			result += "1";
		}
		return result;
	}

}
