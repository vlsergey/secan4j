package io.github.vlsergey.secan4j.core.colorless;

import java.util.Random;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.UserProvided;

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
