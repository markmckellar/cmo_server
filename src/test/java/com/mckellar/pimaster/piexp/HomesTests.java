package com.mckellar.pimaster.piexp;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mckellar.pimaster.piexp.auth.AdminNotifier;
import com.mckellar.pimaster.piexp.auth.GoogleSignIn;

import static org.mockito.Mockito.when;

// Homes and grants in access.json, through the whole server: two homes, and groups granted the world, a home,
// a camera, or an event type on one of those.
@SpringBootTest(properties = {"file.upload-dir=target/homes-uploads", "cmo.auth.access-file=target/homes-access.json",
		"cmo.auth.session-key-file=target/homes-session.key", "cmo.auth.google-client-id=test-client"})
@AutoConfigureMockMvc
class HomesTests {

	static final Path UPLOADS = Paths.get("target/homes-uploads");
	static final Path ACCESS = Paths.get("target/homes-access.json");

	@Autowired
	MockMvc mvc;

	@MockBean
	GoogleSignIn google;

	@MockBean
	AdminNotifier notifier;

	@BeforeEach
	void setUp() throws IOException {
		PimasterApplicationTests.deleteTree(UPLOADS);
		// ours: pet_door (a cat; a person), porch_cam (a person); grandma's: her_porch (a dog; a person); new_cam: in no home
		event("pet_door", "0001", "{\"cat\": true, \"person\": false}");
		event("pet_door", "0002", "{\"person\": true}");
		event("porch_cam", "0003", "{\"person\": true}");
		event("her_porch", "0004", "{\"dog\": true, \"person\": false}");
		event("her_porch", "0005", "{\"person\": true}");
		event("new_cam", "0006", "{\"raccoon\": true, \"person\": false}");
		Files.writeString(ACCESS, """
				{"admins": ["boss@example.com"],
				 "homes": [{"id": "ours", "name": "Our house", "cameras": ["pet_door", "porch_cam"]},
				           {"id": "grandma", "name": "Grandma's", "cameras": ["her_porch"]}],
				 "accounts": [
				   {"id": "us", "members": ["me@example.com"], "grants": [{"home": "ours"}]},
				   {"id": "grandma", "members": ["gran@example.com"], "grants": [{"home": "grandma"}, {"camera": "pet_door", "show": ["cat"]}]},
				   {"id": "fans", "members": ["fan@example.com"], "view_only": true, "grants": [{"world": true, "animals_only": true}]},
				   {"id": "neighbors", "members": ["nb@example.com"], "view_only": true, "grants": [{"camera": "porch_cam"}]},
				   {"id": "broken", "members": ["oops@example.com"], "grants": [{"show": ["cat"]}]}
				 ]}""");
		for (String who : new String[] {"boss", "me", "gran", "fan", "nb", "oops"})
			when(google.check("token-" + who)).thenReturn(Optional.of(new GoogleSignIn.GoogleUser(who + "@example.com", who, "sub-" + who)));
	}

	static void event(String camera, String n, String ollama) throws IOException {
		String name = "20261101_12000" + n.substring(3) + "_" + n;
		Path dir = Files.createDirectories(UPLOADS.resolve(camera).resolve("20261101").resolve(name));
		Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"" + name + "\", \"me_group\": \"" + camera
				+ "\", \"me_event_group\": \"20261101\", \"me_time\": 5.0}");
		Files.writeString(dir.resolve("me_ollama.json"), ollama);
		Files.writeString(dir.resolve("me_rep_image.jpg"), "jpg");
	}

	Cookie signIn(String who) throws Exception {
		return mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\": \"token-" + who + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getCookie("cmo_session");
	}

	void sees(Cookie c, String... events) throws Exception {
		mvc.perform(get("/catcam/search?howMayRecords=20&minimumDuration=0").cookie(c)).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].me_name", containsInAnyOrder(events)));
	}

	static String ev(String n) {
		return "20261101_12000" + n.substring(3) + "_" + n;
	}

	@Test
	void homeGrantSeesItsHomeOnly() throws Exception {
		Cookie me = signIn("me");
		mvc.perform(get("/catcam/megroups").cookie(me)).andExpect(jsonPath("$[*].meGroup", containsInAnyOrder("pet_door", "porch_cam")))
				.andExpect(jsonPath("$[0].homeName").value("Our house"));
		sees(me, ev("0001"), ev("0002"), ev("0003"));
		mvc.perform(get("/catcam/latest/her_porch").cookie(me)).andExpect(status().isForbidden());
		mvc.perform(get("/catcam/data/her_porch/20261101/" + ev("0004") + "/me_rep_image.jpg").cookie(me)).andExpect(status().isForbidden());
		mvc.perform(put("/catcam/event/pet_door/20261101/" + ev("0002") + "/classification").cookie(me)
				.contentType(MediaType.APPLICATION_JSON).content("{\"person\": true}")).andExpect(status().isOk());
	}

	@Test
	void homePlusAnEventTypeOnAnotherCamera() throws Exception {
		Cookie gran = signIn("gran");
		sees(gran, ev("0004"), ev("0005"), ev("0001"));    // all of her porch, and only the cat from the pet door
		mvc.perform(get("/catcam/event/pet_door/20261101/" + ev("0002")).cookie(gran)).andExpect(status().isNotFound());
		mvc.perform(get("/catcam/data/pet_door/20261101/" + ev("0002") + "/me_rep_image.jpg").cookie(gran)).andExpect(status().isNotFound());
		mvc.perform(get("/catcam/latest/porch_cam").cookie(gran)).andExpect(status().isForbidden());
		mvc.perform(get("/auth/config").cookie(gran)).andExpect(jsonPath("$.animals_only").value(false))
				.andExpect(jsonPath("$.access").value("Home Grandma's: all clips · Pet door: cats"));
	}

	@Test
	void worldGrantWithAnEventTypeReachesEveryHomeAndNewCameras() throws Exception {
		Cookie fan = signIn("fan");
		sees(fan, ev("0001"), ev("0004"), ev("0006"));    // animals from every home, and from the camera in no home
		mvc.perform(get("/catcam/search?howMayRecords=20&minimumDuration=0&labels=person").cookie(fan)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/catcam/event/her_porch/20261101/" + ev("0005")).cookie(fan)).andExpect(status().isNotFound());
		mvc.perform(get("/auth/config").cookie(fan)).andExpect(jsonPath("$.animals_only").value(true))
				.andExpect(jsonPath("$.labels", containsInAnyOrder("cat", "dog", "raccoon")));
		mvc.perform(put("/catcam/event/pet_door/20261101/" + ev("0001") + "/classification").cookie(fan)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cat\": true}")).andExpect(status().isForbidden());
	}

	@Test
	void cameraGrantIsThatCameraOnly() throws Exception {
		Cookie nb = signIn("nb");
		sees(nb, ev("0003"));
		mvc.perform(get("/catcam/latest/pet_door").cookie(nb)).andExpect(status().isForbidden());
	}

	@Test
	void aGrantWithNoLevelGrantsNothing() throws Exception {
		Cookie oops = signIn("oops");
		sees(oops);
		mvc.perform(get("/catcam/megroups").cookie(oops)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theAdminSeesEverythingAndTheAccessPageDescribesEachGroup() throws Exception {
		Cookie boss = signIn("boss");
		sees(boss, ev("0001"), ev("0002"), ev("0003"), ev("0004"), ev("0005"), ev("0006"));
		mvc.perform(get("/auth/admin/requests").cookie(boss))
				.andExpect(jsonPath("$.accounts[?(@.id == 'fans')].summary").value("Every camera: cats, dogs, raccoons; no people · view only"))
				.andExpect(jsonPath("$.accounts[?(@.id == 'us')].summary").value("Home Our house: all clips"))
				.andExpect(jsonPath("$.homes[*].id", containsInAnyOrder("ours", "grandma")));
	}
}
