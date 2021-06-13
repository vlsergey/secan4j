package io.github.vlsergey.secan4j.core.springwebmvc;

import java.util.Random;

public class SimpleMethods {

	public String simpleReturn(String src) {
		final String result = src;
		return result;
	}

	public String whileWithRandom() {
		String result = "";
		while (new Random().nextBoolean()) {
			result += "1";
		}
		return result;
	}

}
