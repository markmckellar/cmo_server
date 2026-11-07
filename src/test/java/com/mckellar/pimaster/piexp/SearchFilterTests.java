package com.mckellar.pimaster.piexp;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Stacking search filters: several labels (any or all), labels to leave out, and a date range.
@SpringBootTest(properties = {"file.upload-dir=target/filter-uploads", "cmo.auth.mode=open"})
@AutoConfigureMockMvc
class SearchFilterTests {

	@Autowired
	MockMvc mvc;

	static final String CAT = "{\"cat\": true, \"raccoon\": false, \"person\": false}";
	static final String RACCOON = "{\"cat\": false, \"raccoon\": true, \"person\": false}";
	static final String BOTH = "{\"cat\": true, \"raccoon\": true, \"person\": false}";
	static final String CAT_AND_PERSON = "{\"cat\": true, \"raccoon\": false, \"person\": true}";

	@BeforeAll
	static void makeEvents() throws IOException {
		PimasterApplicationTests.deleteTree(Paths.get("target/filter-uploads"));
		write("20261101", "20261101_010000_0001", RACCOON);
		write("20261102", "20261102_010000_0002", CAT);
		write("20261103", "20261103_010000_0003", BOTH);
		write("20261103", "20261103_020000_0004", CAT_AND_PERSON);
		write("20261104", "20261104_010000_0005", RACCOON);
		write("20261105", "20261105_010000_0006", "{\"cat\": true, \"person\": false, \"legs\": true}");
		java.nio.file.Path porch = java.nio.file.Files.createDirectories(Paths.get("target/filter-uploads", "porch_cam", "20261103", "20261103_015000_0001"));
		java.nio.file.Files.writeString(porch.resolve("me_data.json"), "{\"me_name\": \"20261103_015000_0001\", \"me_group\": \"porch_cam\", "
				+ "\"me_event_group\": \"20261103\", \"me_time\": 5.0}");
		java.nio.file.Files.writeString(porch.resolve("me_ollama.json"), "{\"raccoon\": true, \"person\": false}");
	}

	static void write(String date, String name, String ollama) throws IOException {
		java.nio.file.Path dir = java.nio.file.Files.createDirectories(Paths.get("target/filter-uploads", "pet_door", date, name));
		java.nio.file.Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"" + name + "\", \"me_group\": \"pet_door\", "
				+ "\"me_event_group\": \"" + date + "\", \"me_time\": 5.0}");
		java.nio.file.Files.writeString(dir.resolve("me_ollama.json"), ollama);
	}

	private void expect(String query, String... names) throws Exception {
		mvc.perform(get("/catcam/search/pet_door?howMayRecords=10&minimumDuration=0" + query))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].me_name", contains(names)));
	}

	@Test
	void noFiltersListsNewestFirst() throws Exception {
		expect("", "20261105_010000_0006", "20261104_010000_0005", "20261103_020000_0004", "20261103_010000_0003", "20261102_010000_0002", "20261101_010000_0001");
	}

	@Test
	void anyOfSeveralLabels() throws Exception {
		expect("&labels=raccoon,cat", "20261105_010000_0006", "20261104_010000_0005", "20261103_020000_0004", "20261103_010000_0003", "20261102_010000_0002", "20261101_010000_0001");
		expect("&labels=raccoon", "20261104_010000_0005", "20261103_010000_0003", "20261101_010000_0001");
	}

	@Test
	void allLabelsInTheSameEvent() throws Exception {
		expect("&labels=raccoon,cat&match=all", "20261103_010000_0003");
	}

	@Test
	void without() throws Exception {
		expect("&labels=cat&without=person", "20261103_010000_0003", "20261102_010000_0002");    // 0006 has legs
		expect("&without=raccoon,person", "20261102_010000_0002");    // 0006 has legs
	}

	@Test
	void dateRangeAndOneDay() throws Exception {
		expect("&from=20261102&to=20261103", "20261103_020000_0004", "20261103_010000_0003", "20261102_010000_0002");
		expect("&from=2026-11-03&to=2026-11-03&labels=cat", "20261103_020000_0004", "20261103_010000_0003");
		expect("&from=20261104&to=20261104", "20261104_010000_0005");
		expect("&to=20261101", "20261101_010000_0001");
	}

	@Test
	void pagesWithBefore() throws Exception {
		mvc.perform(get("/catcam/search/pet_door?howMayRecords=2&minimumDuration=0&labels=cat&to=20261104"))
				.andExpect(jsonPath("$[*].me_name", contains("20261103_020000_0004", "20261103_010000_0003")));
		expect("&labels=cat&before=20261103_010000_0003", "20261102_010000_0002");
	}

	@Test
	void oldOneLabelFormStillWorks() throws Exception {
		expect("&label=RACCOON&from=20261102", "20261104_010000_0005", "20261103_010000_0003");
	}

	@Test
	void personAlsoMeansLegs() throws Exception {
		expect("&labels=person", "20261105_010000_0006", "20261103_020000_0004");
		expect("&labels=legs", "20261105_010000_0006");
		expect("&labels=cat&without=person&to=20261105", "20261103_010000_0003", "20261102_010000_0002");
	}

	@Test
	void resultsCarryTheirLabels() throws Exception {
		mvc.perform(get("/catcam/search/pet_door?howMayRecords=1&minimumDuration=0&labels=raccoon"))
				.andExpect(jsonPath("$[0].me_ollama.raccoon").value(true));
	}

	@Test
	void aRelabeledEventIsFoundRightAway() throws Exception {
		mvc.perform(get("/catcam/search/pet_door?howMayRecords=10&minimumDuration=0&labels=dog"))
				.andExpect(jsonPath("$.length()").value(0));
		String path = "/catcam/event/pet_door/20261102/20261102_010000_0002/classification";
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"cat\": true, \"dog\": true, \"person\": false}"))
				.andExpect(status().isOk());
		try {
			expect("&labels=dog", "20261102_010000_0002");
		}
		finally {
			mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(CAT)).andExpect(status().isOk());
		}
	}

	@Test
	void theIndexGivesTheSameAnswers() throws Exception {
		// the index is built in the background at startup; wait for it, then the same searches go through it
		for (int i = 0; i < 100; i++) {
			String health = mvc.perform(get("/catcam/health")).andReturn().getResponse().getContentAsString();
			if (health.contains("\"ready\":true")) break;
			Thread.sleep(50);
		}
		mvc.perform(get("/catcam/health")).andExpect(jsonPath("$.label_index.ready").value(true))
				.andExpect(jsonPath("$.label_index.events").value(7));
		anyOfSeveralLabels();
		allLabelsInTheSameEvent();
		without();
		dateRangeAndOneDay();
		pagesWithBefore();
		oldOneLabelFormStillWorks();
		personAlsoMeansLegs();
	}

	@Test
	void allCamerasMergedNewestFirst() throws Exception {
		mvc.perform(get("/catcam/search?howMayRecords=10&minimumDuration=0&labels=raccoon"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].me_name", contains("20261104_010000_0005", "20261103_015000_0001", "20261103_010000_0003", "20261101_010000_0001")))
				.andExpect(jsonPath("$[1].me_group").value("porch_cam"));
		mvc.perform(get("/catcam/search?howMayRecords=10&minimumDuration=0&labels=raccoon&cameras=porch_cam"))
				.andExpect(jsonPath("$[*].me_name", contains("20261103_015000_0001")));
		mvc.perform(get("/catcam/search?howMayRecords=10&minimumDuration=0&labels=raccoon&cameras=pet_door,porch_cam"))
				.andExpect(jsonPath("$.length()").value(4));
		mvc.perform(get("/catcam/search?howMayRecords=2&minimumDuration=0&labels=raccoon&before=20261104_010000_0005"))
				.andExpect(jsonPath("$[*].me_name", contains("20261103_015000_0001", "20261103_010000_0003")));
	}

	@Test
	void badDateIsRefused() throws Exception {
		mvc.perform(get("/catcam/search/pet_door?howMayRecords=10&minimumDuration=0&from=yesterday")).andExpect(status().isBadRequest());
	}
}
