package com.mckellar.pimaster.piexp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

// The interim setting on cmoserver01 until Google sign-in exists: anyone may view, nobody may change anything
// without a key (or, later, a login).
@SpringBootTest(properties = {"file.upload-dir=target/test-uploads", "cmo.auth.public-read=true",
		"cmo.auth.access-file=target/no-such-access.json"})
@AutoConfigureMockMvc
class PublicReadTests {

	@Autowired
	MockMvc mvc;

	@Test
	void anyoneMayView() throws Exception {
		mvc.perform(get("/catcam/megroups")).andExpect(status().isOk());
		mvc.perform(get("/auth/config")).andExpect(jsonPath("$.public_read").value(true));
	}

	@Test
	void nobodyMayChangeAnythingWithoutAKey() throws Exception {
		mvc.perform(multipart("/uploadme/pet_door/20261102/20261102_080000_0001")
				.file(new MockMultipartFile("file", "me_data.json", "application/json", "{}".getBytes())))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/catcam/event/pet_door/20261010/20261010_120000_0001")).andExpect(status().isUnauthorized());
	}
}
