package com.mckellar.pimaster.piexp;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.test.web.servlet.MockMvc;

// Same package as PimasterApplication, so @SpringBootTest finds its configuration.
// Uploads go under target/ so a test run never touches real event data.
@SpringBootTest(properties = "file.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
class PimasterApplicationTests {

	static final Path UPLOADS = Paths.get("target/test-uploads");

	@Autowired
	MockMvc mvc;

	// Two pet_door events on two dates (the newest one with a cat) and one porch event,
	// written straight to disk the way the uploads leave them.
	@BeforeAll
	static void makeEvents() throws IOException {
		deleteTree(UPLOADS);
		event("pet_door", "20261010", "20261010_120000_0001", 5.0, "{\"cat\": false, \"person\": true}", true);
		event("pet_door", "20261011", "20261011_080000_0002", 2.0, "{\"cat\": true, \"person\": false}", true);
		event("porch", "20261011", "20261011_090000_0001", 7.0, "{\"cat\": false, \"person\": true}", false);
		Files.writeString(UPLOADS.resolve("stray_file.txt"), "not a camera");
	}

	static void event(String group, String date, String name, double secs, String ollama, boolean withMovie) throws IOException {
		Path dir = Files.createDirectories(UPLOADS.resolve(group).resolve(date).resolve(name));
		Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"" + name + "\", \"me_group\": \"" + group
				+ "\", \"me_event_group\": \"" + date + "\", \"me_time\": " + secs
				+ ", \"me_video_name\": \"me_movie.mp4\", \"me_rep_image\": \"me_rep_image.jpg\","
				+ " \"me_delta_array\": [{\"event_time_iso\": \"a\"}, {\"event_time_iso\": \"b\"}]}");
		Files.writeString(dir.resolve("me_ollama.json"), ollama);
		Files.writeString(dir.resolve("me_rep_image.jpg"), "jpg");
		if (withMovie) Files.writeString(dir.resolve("me_movie.mp4"), "mp4");
	}

	static void deleteTree(Path p) throws IOException {
		if (!Files.exists(p)) return;
		try (var walk = Files.walk(p)) {
			for (Path f : walk.sorted((a, b) -> b.compareTo(a)).toArray(Path[]::new)) Files.delete(f);
		}
	}

	@Test
	void groupsAreFoldersOnly() throws Exception {
		mvc.perform(get("/catcam/megroups")).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].meGroup", hasItems("pet_door", "porch")))
				.andExpect(jsonPath("$[*].meGroup", not(hasItem("stray_file.txt"))));
	}

	@Test
	void datesNewestFirst() throws Exception {
		mvc.perform(get("/catcam/meeventgroup/pet_door?howMayRecords=10")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].meEventGroup").value("20261011"))
				.andExpect(jsonPath("$[1].meEventGroup").value("20261010"));
	}

	@Test
	void unknownCameraOrDateIsAnEmptyList() throws Exception {
		mvc.perform(get("/catcam/meeventgroup/no_such_camera?howMayRecords=10"))
				.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/catcam/meeventgroup/pet_door/19990101?howMayRecords=10&startRecord=0&minimumDuration=0"))
				.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/catcam/search/no_such_camera?label=cat&howMayRecords=10&minimumDuration=0"))
				.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void eventsOfADate() throws Exception {
		mvc.perform(get("/catcam/meeventgroup/pet_door/20261010?howMayRecords=10&startRecord=0&minimumDuration=0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].me_name").value("20261010_120000_0001"))
				.andExpect(jsonPath("$[0]", hasKey("me_delta_array")));
		mvc.perform(get("/catcam/meeventgroup/pet_door/20261010?howMayRecords=10&startRecord=0&minimumDuration=0&brief=true"))
				.andExpect(jsonPath("$[0]", not(hasKey("me_delta_array"))))
				.andExpect(jsonPath("$[0].me_delta_count").value(2));
		mvc.perform(get("/catcam/meeventgroup/pet_door/20261010?howMayRecords=10&startRecord=0&minimumDuration=9"))
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void searchByLabel() throws Exception {
		mvc.perform(get("/catcam/search/pet_door?label=CAT&howMayRecords=10&minimumDuration=0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].me_name").value("20261011_080000_0002"));
	}

	@Test
	void oneEvent() throws Exception {
		mvc.perform(get("/catcam/event/pet_door/20261011/20261011_080000_0002")).andExpect(status().isOk())
				.andExpect(jsonPath("$.complete").value(true))
				.andExpect(jsonPath("$.files", hasKey("me_movie.mp4")))
				.andExpect(jsonPath("$.me_ollama.cat").value(true))
				.andExpect(jsonPath("$.me_data.me_delta_count").value(2));
		// the porch event has no movie yet
		mvc.perform(get("/catcam/event/porch/20261011/20261011_090000_0001"))
				.andExpect(jsonPath("$.complete").value(false));
		mvc.perform(get("/catcam/event/pet_door/20261011/no_such_event")).andExpect(status().isNotFound());
	}

	@Test
	void latestAcrossCameras() throws Exception {
		mvc.perform(get("/catcam/latest?n=2")).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].me_name").value("20261011_090000_0001"))
				.andExpect(jsonPath("$[0].me_ollama.person").value(true))
				.andExpect(jsonPath("$[0]", not(hasKey("me_delta_array"))))
				.andExpect(jsonPath("$[1].me_name").value("20261011_080000_0002"));
		mvc.perform(get("/catcam/latest/pet_door")).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void health() throws Exception {
		mvc.perform(get("/catcam/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"))
				.andExpect(jsonPath("$.groups.pet_door.dates").value(2))
				.andExpect(jsonPath("$.groups.pet_door.newest_event").value("20261011_080000_0002"))
				.andExpect(jsonPath("$.groups", not(hasKey("stray_file.txt"))));
	}

	@Test
	void uploadAndDownload() throws Exception {
		MockMultipartFile json = new MockMultipartFile("file", "me_ollama.json", "application/json",
				"{\"dog\": true}".getBytes(StandardCharsets.UTF_8));
		mvc.perform(multipart("/uploadme/test_cam/20261012/20261012_100000_0001").file(json).param("image_dir", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("test_cam/20261012/20261012_100000_0001/me_ollama.json"));
		Path event = UPLOADS.resolve("test_cam/20261012/20261012_100000_0001");
		assertTrue(Files.exists(event.resolve("me_ollama.json")));
		assertFalse(Files.exists(event.resolve("images")), "no empty images/ folder");

		// old Pi clients send frames with image_dir set
		MockMultipartFile frame = new MockMultipartFile("file", "frame_0001.jpg", "image/jpeg", new byte[] {1, 2, 3});
		mvc.perform(multipart("/uploadme/test_cam/20261012/20261012_100000_0001").file(frame).param("image_dir", "images"))
				.andExpect(status().isOk());
		assertTrue(Files.exists(event.resolve("images/frame_0001.jpg")));

		mvc.perform(get("/catcam/data/test_cam/20261012/20261012_100000_0001/me_ollama.json"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.dog").value(true));
		mvc.perform(get("/catcam/data/test_cam/20261012/20261012_100000_0001/images/frame_0001.jpg"))
				.andExpect(status().isOk());
		mvc.perform(get("/catcam/data/test_cam/20261012/20261012_100000_0001/missing.mp4"))
				.andExpect(status().isNotFound());
	}

	@Test
	void pathsCantLeaveTheUploadsFolder() throws Exception {
		// ".." in the URL is refused by Spring Security's firewall before it gets here
		MockMultipartFile one = new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {1});
		assertThrows(RequestRejectedException.class,
				() -> mvc.perform(multipart("/uploadme/{g}/{d}/{n}", "..", "..", "..").file(one)));
		// and a file name that climbs out is refused by the server itself
		MockMultipartFile evil = new MockMultipartFile("file", "../../../../../escaped.txt", "text/plain", new byte[] {1});
		mvc.perform(multipart("/uploadme/a/b/c").file(evil)).andExpect(status().isBadRequest());
		assertFalse(Files.exists(UPLOADS.resolve("../escaped.txt").normalize()));
		assertFalse(Files.exists(UPLOADS.resolve("../../escaped.txt").normalize()));
	}
}
