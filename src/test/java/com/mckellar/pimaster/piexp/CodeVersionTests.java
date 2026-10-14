package com.mckellar.pimaster.piexp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CodeVersionTests {

	@Test
	void formatsGitDescribe() {
		assertEquals("1.3.0", CodeVersion.format("v1.3.0-0-gabc1234"));
		assertEquals("1.3.0+4", CodeVersion.format("v1.3.0-4-gabc1234"));
		assertEquals("1.3.0+4.dirty", CodeVersion.format("v1.3.0-4-gabc1234-dirty"));
		assertEquals("1.3.0+dirty", CodeVersion.format("v1.3.0-0-gabc1234-dirty"));
		assertEquals("unknown", CodeVersion.format(""));
		assertEquals("unknown", CodeVersion.format("abc1234"));
	}
}
