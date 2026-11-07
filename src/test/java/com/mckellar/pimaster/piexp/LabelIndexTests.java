package com.mckellar.pimaster.piexp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// The label index on its own: what it finds, and how it copes when the folders change under it.
class LabelIndexTests {

	static final Path ROOT = Paths.get("target/index-uploads");
	static final Type MAP = new TypeToken<Map<String, Object>>() {}.getType();

	LabelIndex index;

	@BeforeEach
	void setUp() throws IOException {
		PimasterApplicationTests.deleteTree(ROOT);
		write("20261101", "20261101_010000_0001", "{\"raccoon\": true, \"person\": false}");
		write("20261102", "20261102_010000_0002", "{\"cat\": true, \"person\": false}");
		write("20261103", "20261103_010000_0003", "{\"cat\": true, \"raccoon\": true}");
		index = new LabelIndex(ROOT.toFile(), dir -> {
			try {
				return new Gson().fromJson(Files.readString(dir.toPath().resolve("me_ollama.json")), MAP);
			}
			catch (Exception e) {
				return null;    // unreadable, like the server's readOllama
			}
		});
	}

	static File write(String date, String name, String ollama) throws IOException {
		Path dir = Files.createDirectories(ROOT.resolve("pet_door").resolve(date).resolve(name));
		if (ollama != null) Files.writeString(dir.resolve("me_ollama.json"), ollama);
		return dir.toFile();
	}

	List<String> find(String... labels) {
		return index.candidates("pet_door", List.of(labels), false, null, null, null).stream().map(c -> c[1]).collect(Collectors.toList());
	}

	@Test
	void notReadyUntilBuilt() {
		assertFalse(index.isReady());
		index.rebuild();
		assertTrue(index.isReady());
		assertEquals(3, index.describe().get("events"));
	}

	@Test
	void findsLabelsNewestFirst() {
		index.rebuild();
		assertEquals(List.of("20261103_010000_0003", "20261101_010000_0001"), find("raccoon"));
		assertEquals(List.of("20261103_010000_0003", "20261102_010000_0002", "20261101_010000_0001"), find("raccoon", "cat"));
		assertEquals(List.of("20261103_010000_0003"),
				index.candidates("pet_door", List.of("raccoon", "cat"), true, null, null, null).stream().map(c -> c[1]).toList());
		assertEquals(List.of("20261101_010000_0001"),
				index.candidates("pet_door", List.of("raccoon"), false, "20261101", "20261102", null).stream().map(c -> c[1]).toList());
		assertEquals(List.of("20261101_010000_0001"),
				index.candidates("pet_door", List.of("raccoon"), false, null, null, "20261103_010000_0003").stream().map(c -> c[1]).toList());
		assertEquals(List.of(), find("dog"));
		assertEquals(List.of(), index.candidates("no_camera", List.of("cat"), false, null, null, null));
	}

	@Test
	void anUploadIsFoundRightAway() throws IOException {
		index.rebuild();
		index.refresh(write("20261104", "20261104_010000_0004", "{\"dog\": true}"));
		assertEquals(List.of("20261104_010000_0004"), find("dog"));
	}

	@Test
	void aChangeBehindItsBackIsCaughtWhenAskedOrAtTheNextRecheck() throws IOException {
		index.rebuild();
		File dir = ROOT.resolve("pet_door/20261102/20261102_010000_0002").toFile();
		Files.writeString(dir.toPath().resolve("me_ollama.json"), "{\"cat\": false, \"dog\": true}");
		dir.toPath().resolve("me_ollama.json").toFile().setLastModified(System.currentTimeMillis() + 5000);
		// a search re-checks each event it is about to show: the stale "cat" entry is fixed, not shown
		assertEquals(Boolean.FALSE, index.labels(dir).get("cat"));
		assertEquals(List.of("20261103_010000_0003"), find("cat"));
		// an event the index never saw turns up at the next re-check
		write("20261105", "20261105_010000_0005", "{\"dog\": true}");
		index.rebuild();
		assertEquals(List.of("20261105_010000_0005", "20261102_010000_0002"), find("dog"));
	}

	@Test
	void deletedEventsAndDatesDropOut() throws IOException {
		index.rebuild();
		PimasterApplicationTests.deleteTree(ROOT.resolve("pet_door/20261103"));
		// asked about directly (a search re-checking it), a missing event drops out at once
		assertNull(index.labels(ROOT.resolve("pet_door/20261103/20261103_010000_0003").toFile()));
		assertEquals(List.of("20261101_010000_0001"), find("raccoon"));
		// and anything else gone is dropped at the next re-check
		PimasterApplicationTests.deleteTree(ROOT.resolve("pet_door/20261101"));
		index.rebuild();
		assertEquals(List.of(), find("raccoon"));
		assertEquals(1, index.describe().get("events"));
	}

	@Test
	void unreadableOrMissingLabelsAreNoLabelsNotAnError() throws IOException {
		write("20261106", "20261106_010000_0006", "{not json");
		write("20261107", "20261107_010000_0007", null);
		index.rebuild();
		assertTrue(index.isReady());
		assertNull(index.labels(ROOT.resolve("pet_door/20261106/20261106_010000_0006").toFile()));
		assertNull(index.labels(ROOT.resolve("pet_door/20261107/20261107_010000_0007").toFile()));
		// fixed later (e.g. the classification arrives): picked up
		Files.writeString(ROOT.resolve("pet_door/20261107/20261107_010000_0007/me_ollama.json"), "{\"raccoon\": true}");
		index.rebuild();
		assertEquals("20261107_010000_0007", find("raccoon").get(0));
	}

	@Test
	void aMissingUploadsFolderIsAnEmptyIndex() {
		LabelIndex empty = new LabelIndex(new File("target/no-such-folder"), d -> null);
		empty.rebuild();
		assertTrue(empty.isReady());
		assertEquals(0, empty.describe().get("events"));
	}
}
