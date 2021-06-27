package io.github.vlsergey.secan4j.core.colored;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.UserProvided;

public class SimpleColoredMethods {

	@Command
	public String concatenation(@UserProvided String a, @UserProvided String b) {
		final String result = a + b;
		return result;
	}

	public void append(@UserProvided String src, StringBuilder dst) {
		dst.append(src);
	}

}
