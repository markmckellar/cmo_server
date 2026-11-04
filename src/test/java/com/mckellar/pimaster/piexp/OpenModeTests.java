package com.mckellar.pimaster.piexp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.mckellar.pimaster.piexp.auth.AuthConfig;

// open mode (ada's copy at home): anything goes from the home network, nothing from anywhere else.
@SpringBootTest(properties = {"file.upload-dir=target/test-uploads", "cmo.auth.mode=open"})
@AutoConfigureMockMvc
class OpenModeTests {

	@Autowired
	MockMvc mvc;

	@Test
	void homeNetworkMayDoAnythingKeysIgnored() throws Exception {
		mvc.perform(get("/catcam/megroups")).andExpect(status().isOk());
		mvc.perform(get("/catcam/megroups").header("Authorization", "Bearer whatever")).andExpect(status().isOk());
		mvc.perform(get("/auth/config")).andExpect(jsonPath("$.mode").value("open")).andExpect(jsonPath("$.caller").value("local"));
		mvc.perform(get("/catcam/megroups").with(r -> { r.setRemoteAddr("192.168.1.50"); return r; })).andExpect(status().isOk());
	}

	@Test
	void internetAndProxiedRequestsAreRefused() throws Exception {
		mvc.perform(get("/catcam/megroups").with(r -> { r.setRemoteAddr("8.8.8.8"); return r; })).andExpect(status().isForbidden());
		mvc.perform(get("/catcam/health").header("X-Forwarded-For", "8.8.8.8")).andExpect(status().isForbidden());
	}

	@Test
	void refusesToStartOnAPublicAddress() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> new AuthConfig("open", false, "x", "8.8.8.8"));
		new AuthConfig("open", false, "x", "192.168.1.167");
		new AuthConfig("open", false, "x", "0.0.0.0");
	}
}
