package com.mckellar.pimaster.piexp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Same package as PimasterApplication, so @SpringBootTest finds its configuration.
// Uploads go under target/ so a test run never touches real event data.
@SpringBootTest(properties = "file.upload-dir=target/test-uploads")
class PimasterApplicationTests {

	@Test
	void contextLoads() {
	}

}
