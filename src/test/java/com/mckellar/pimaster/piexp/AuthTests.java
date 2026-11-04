package com.mckellar.pimaster.piexp;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.mckellar.pimaster.piexp.auth.JsonAccessStore;

// google mode (the default), no public read: what cmoserver01 will run once Google sign-in exists.
@SpringBootTest(properties = {"file.upload-dir=target/auth-uploads", "cmo.auth.access-file=target/auth-access.json"})
@AutoConfigureMockMvc
class AuthTests {

	static final Path UPLOADS = Paths.get("target/auth-uploads");
	static final String PET_DOOR_KEY = "cmo_test_pet_door_key";
	static final String OLD_PET_DOOR_KEY = "cmo_test_pet_door_old_key";
	static final String READER_KEY = "cmo_test_reader_key";
	static final String NEIGHBOR_KEY = "cmo_test_neighbor_key";

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void setUp() throws IOException {
		PimasterApplicationTests.deleteTree(UPLOADS);
		for (String g : new String[] {"pet_door", "porch_cam", "neighbor_cam"}) {
			Path dir = Files.createDirectories(UPLOADS.resolve(g).resolve("20261101").resolve("20261101_120000_0001"));
			Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"20261101_120000_0001\", \"me_group\": \"" + g
					+ "\", \"me_event_group\": \"20261101\", \"me_time\": 1.0}");
			Files.writeString(dir.resolve("me_rep_image.jpg"), "jpg");
		}
		Files.writeString(Paths.get("target/auth-access.json"), "{"
				+ "\"accounts\": ["
				+ "  {\"id\": \"home\", \"members\": [\"me@example.com\"], \"cameras\": [\"pet_door\", \"porch_cam\"]},"
				+ "  {\"id\": \"neighbor\", \"members\": [], \"cameras\": [\"neighbor_cam\"]}],"
				+ "\"devices\": ["
				+ "  {\"id\": \"pet_door_pi\", \"account\": \"home\", \"role\": \"camera\", \"cameras\": [\"pet_door\"],"
				+ "   \"key_hashes\": [\"" + JsonAccessStore.hashKey(PET_DOOR_KEY) + "\", \"" + JsonAccessStore.hashKey(OLD_PET_DOOR_KEY) + "\"]},"
				+ "  {\"id\": \"scripts\", \"account\": \"home\", \"role\": \"reader\", \"key_hashes\": [\"" + JsonAccessStore.hashKey(READER_KEY) + "\"]},"
				+ "  {\"id\": \"neighbor_pi\", \"account\": \"neighbor\", \"role\": \"camera\", \"cameras\": [\"neighbor_cam\"],"
				+ "   \"key_hashes\": [\"" + JsonAccessStore.hashKey(NEIGHBOR_KEY) + "\"]}]}");
	}

	static String bearer(String key) {
		return "Bearer " + key;
	}

	static MockMultipartFile file() {
		return new MockMultipartFile("file", "me_data.json", "application/json", "{}".getBytes());
	}

	@Test
	void publicPathsNeedNoKey() throws Exception {
		mvc.perform(get("/catcam/health")).andExpect(status().isOk()).andExpect(jsonPath("$.auth.mode").value("google"));
		mvc.perform(get("/catcam/version")).andExpect(status().isOk());
		mvc.perform(get("/auth/config")).andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("google")).andExpect(jsonPath("$.caller").value("anonymous"));
	}

	@Test
	void viewingNeedsALogin() throws Exception {
		mvc.perform(get("/catcam/megroups")).andExpect(status().isUnauthorized());
		mvc.perform(get("/catcam/latest/pet_door")).andExpect(status().isUnauthorized());
		mvc.perform(get("/catcam/data/pet_door/20261101/20261101_120000_0001/me_rep_image.jpg")).andExpect(status().isUnauthorized());
	}

	@Test
	void cameraUploadsOnlyToItsOwnCamera() throws Exception {
		mvc.perform(multipart("/uploadme/pet_door/20261102/20261102_080000_0001").file(file())
				.header("Authorization", bearer(PET_DOOR_KEY))).andExpect(status().isOk());
		mvc.perform(multipart("/uploadme/porch_cam/20261102/20261102_080000_0001").file(file())
				.header("Authorization", bearer(PET_DOOR_KEY))).andExpect(status().isForbidden());
		mvc.perform(multipart("/uploadme/pet_door/20261102/20261102_080000_0002").file(file()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void eitherOfTwoKeysWorks() throws Exception {
		mvc.perform(multipart("/uploadme/pet_door/20261102/20261102_090000_0001").file(file())
				.header("Authorization", bearer(OLD_PET_DOOR_KEY))).andExpect(status().isOk());
	}

	@Test
	void wrongKeyIsRefusedEverywhere() throws Exception {
		mvc.perform(get("/catcam/health").header("Authorization", bearer("nope"))).andExpect(status().isUnauthorized());
	}

	@Test
	void cameraCannotView() throws Exception {
		mvc.perform(get("/catcam/latest/pet_door").header("Authorization", bearer(PET_DOOR_KEY))).andExpect(status().isForbidden());
		mvc.perform(get("/catcam/health").header("Authorization", bearer(PET_DOOR_KEY))).andExpect(status().isOk());
	}

	@Test
	void readerSeesOnlyItsAccount() throws Exception {
		mvc.perform(get("/catcam/megroups").header("Authorization", bearer(READER_KEY))).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].meGroup", contains("pet_door", "porch_cam")));
		mvc.perform(get("/catcam/latest/porch_cam").header("Authorization", bearer(READER_KEY))).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/catcam/latest?n=10").header("Authorization", bearer(READER_KEY))).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(get("/catcam/latest/neighbor_cam").header("Authorization", bearer(READER_KEY))).andExpect(status().isForbidden());
		mvc.perform(get("/catcam/data/neighbor_cam/20261101/20261101_120000_0001/me_rep_image.jpg")
				.header("Authorization", bearer(READER_KEY))).andExpect(status().isForbidden());
	}

	@Test
	void devicesCannotChangeOrDelete() throws Exception {
		mvc.perform(delete("/catcam/event/pet_door/20261101/20261101_120000_0001").header("Authorization", bearer(READER_KEY)))
				.andExpect(status().isForbidden());
		mvc.perform(put("/catcam/event/pet_door/20261101/20261101_120000_0001/classification").content("{}")
				.header("Authorization", bearer(PET_DOOR_KEY))).andExpect(status().isForbidden());
		mvc.perform(delete("/catcam/event/pet_door/20261101/20261101_120000_0001")).andExpect(status().isUnauthorized());
	}
}
