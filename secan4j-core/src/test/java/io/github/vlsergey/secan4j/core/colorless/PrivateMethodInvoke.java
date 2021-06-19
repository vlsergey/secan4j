package io.github.vlsergey.secan4j.core.colorless;

public class PrivateMethodInvoke {

	public static class Foo {
		public static void testInnerMethod() {
			testOuterMethod();
		}
	}

	private static void testOuterMethod() {
		return;
	}

}
