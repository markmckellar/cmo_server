package com.mckellar.pimaster.piexp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;

import com.mckellar.pimaster.piexp.auth.AdminNotifier;
import com.mckellar.pimaster.piexp.auth.GoogleSignIn;

// google mode, no public read, with a stand-in for Google and for the Telegram alert.
@SpringBootTest(properties = {"file.upload-dir=target/signin-uploads", "cmo.auth.access-file=target/signin-access.json",
		"cmo.auth.session-key-file=target/signin-session.key", "cmo.auth.google-client-id=test-client"})
@AutoConfigureMockMvc
class SignInTests {

	static final Path UPLOADS = Paths.get("target/signin-uploads");
	static final Path ACCESS = Paths.get("target/signin-access.json");

	@Autowired
	MockMvc mvc;

	@MockBean
	GoogleSignIn google;

	@MockBean
	AdminNotifier notifier;

	@BeforeEach
	void setUp() throws IOException {
		PimasterApplicationTests.deleteTree(UPLOADS);
		for (String g : new String[] {"pet_door", "neighbor_cam"}) {
			Path dir = Files.createDirectories(UPLOADS.resolve(g).resolve("20261101").resolve("20261101_120000_0001"));
			Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"20261101_120000_0001\", \"me_group\": \"" + g
					+ "\", \"me_event_group\": \"20261101\", \"me_time\": 1.0}");
			Files.writeString(dir.resolve("me_ollama.json"), "{\"cat\": true}");
		}
		Files.writeString(ACCESS, "{\"admins\": [\"boss@example.com\"],"
				+ "\"accounts\": [{\"id\": \"home\", \"members\": [\"boss@example.com\"], \"cameras\": [\"pet_door\"]},"
				+ "  {\"id\": \"neighbor\", \"members\": [], \"cameras\": [\"neighbor_cam\"]},"
				+ "  {\"id\": \"friends\", \"members\": [], \"cameras\": [\"pet_door\"], \"view_only\": true, \"animals_only\": true}]}");
		// more pet door events for the friends household: a raccoon (shown), a cat with a person (hidden), never classified (hidden)
		String[][] more = {{"0002", "{\"person\": false, \"raccoon\": true, \"animal\": true}"},
				{"0003", "{\"person\": true, \"cat\": true}"}, {"0004", null},
				{"0005", "{\"person\": false, \"legs\": true, \"cat\": true}"},       // a person's legs: hidden
				{"0006", "{\"person\": false, \"delivery\": true, \"dog\": true}"}};  // a delivery: hidden
		for (String[] e : more) {
			String name = "20261101_12000" + e[0].substring(3) + "_" + e[0];
			Path dir = Files.createDirectories(UPLOADS.resolve("pet_door").resolve("20261101").resolve(name));
			Files.writeString(dir.resolve("me_data.json"), "{\"me_name\": \"" + name + "\", \"me_group\": \"pet_door\", "
					+ "\"me_event_group\": \"20261101\", \"me_time\": 1.0}");
			Files.writeString(dir.resolve("me_rep_image.jpg"), "jpg");
			if (e[1] != null) Files.writeString(dir.resolve("me_ollama.json"), e[1]);
		}
		reset(notifier);
		for (String who : new String[] {"boss", "newbie", "spammer"})
			when(google.check("token-" + who)).thenReturn(Optional.of(new GoogleSignIn.GoogleUser(who + "@example.com", who, "sub-" + who)));
		when(google.check("bad-token")).thenReturn(Optional.empty());
	}

	@Test
	void frontDoorOpensTheWebApp() throws Exception {
		for (String p : new String[] {"/", "/cmo_client", "/cmo_client/"})
			mvc.perform(get(p)).andExpect(status().isFound()).andExpect(header().string("Location", "/cmo_client/index.html"));
		mvc.perform(get("/?event=x")).andExpect(header().string("Location", "/cmo_client/index.html?event=x"));
	}

	Cookie signIn(String who, String expectStatus) throws Exception {
		MvcResult r = mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\": \"token-" + who + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expectStatus))
				.andExpect(header().string("Set-Cookie", containsString("HttpOnly"))).andReturn();
		return r.getResponse().getCookie("cmo_session");
	}

	@Test
	void badTokenIsRefused() throws Exception {
		mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\": \"bad-token\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void strangerWaitsAndTheAdminIsAlertedOnce() throws Exception {
		Cookie c = signIn("newbie", "pending");
		signIn("newbie", "pending");    // second sign-in: still waiting, no second alert
		verify(notifier, times(1)).send(anyString());
		mvc.perform(get("/catcam/megroups").cookie(c)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("waiting for approval"));
		mvc.perform(get("/auth/config").cookie(c)).andExpect(jsonPath("$.status").value("pending"));
		org.junit.jupiter.api.Assertions.assertTrue(Files.readString(ACCESS).contains("newbie@example.com"));
	}

	@Test
	void browserIsSentToSignInAndBack() throws Exception {
		mvc.perform(get("/catcam/data/pet_door/20261101/20261101_120000_0001/me_data.json").accept(MediaType.TEXT_HTML))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", startsWith("/signin.html?next=%2Fcatcam%2Fdata%2Fpet_door")));
		mvc.perform(get("/catcam/megroups")).andExpect(status().isUnauthorized());    // a script gets 401, not a page
	}

	@Test
	void approvedMemberSeesAndRelabelsOnlyTheirCameras() throws Exception {
		Cookie newbie = signIn("newbie", "pending");
		Cookie boss = signIn("boss", "admin");
		mvc.perform(post("/auth/admin/approve").cookie(newbie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"newbie@example.com\", \"account\": \"home\"}")).andExpect(status().isForbidden());
		mvc.perform(post("/auth/admin/approve").cookie(boss).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"newbie@example.com\", \"account\": \"home\"}")).andExpect(status().isOk());

		mvc.perform(get("/catcam/megroups").cookie(newbie)).andExpect(status().isOk()).andExpect(jsonPath("$[0].meGroup").value("pet_door"));
		mvc.perform(get("/catcam/latest/neighbor_cam").cookie(newbie)).andExpect(status().isForbidden());
		mvc.perform(put("/catcam/event/pet_door/20261101/20261101_120000_0001/classification").cookie(newbie)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cat\": false, \"dog\": true}")).andExpect(status().isOk());
		mvc.perform(delete("/catcam/event/pet_door/20261101/20261101_120000_0001").cookie(newbie)).andExpect(status().isForbidden());
		mvc.perform(get("/auth/config").cookie(newbie)).andExpect(jsonPath("$.status").value("member"));
	}

	@Test
	void friendsSeeOnlyAnimalsAndMayNotRelabel() throws Exception {
		Cookie friend = signIn("newbie", "pending");
		Cookie boss = signIn("boss", "admin");
		mvc.perform(post("/auth/admin/approve").cookie(boss).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"newbie@example.com\", \"account\": \"friends\"}")).andExpect(status().isOk());
		mvc.perform(get("/auth/config").cookie(friend)).andExpect(jsonPath("$.view_only").value(true))
				.andExpect(jsonPath("$.animals_only").value(true));

		// lists: only the raccoon (0001 has no "person" label, 0003 has a person, 0004 was never classified)
		mvc.perform(get("/catcam/latest/pet_door").cookie(friend)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].me_name").value("20261101_120002_0002"));
		mvc.perform(get("/catcam/latest").cookie(friend)).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/catcam/meeventgroup/pet_door/20261101?howMayRecords=10&startRecord=0&minimumDuration=0").cookie(friend))
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/catcam/search/pet_door?label=cat&howMayRecords=10&minimumDuration=0").cookie(friend))
				.andExpect(jsonPath("$.length()").value(0));
		// asking for people outright, or for nothing in particular, still finds only the raccoon
		for (String q : new String[] {"labels=person", "labels=legs", "labels=delivery", "labels=cat,dog,person,legs", "without=raccoon", ""})
			mvc.perform(get("/catcam/search/pet_door?howMayRecords=10&minimumDuration=0&" + q).cookie(friend))
					.andExpect(jsonPath("$[*].me_name", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("20261101_120002_0002"))));
		mvc.perform(get("/catcam/search/pet_door?label=raccoon&howMayRecords=10&minimumDuration=0").cookie(friend))
				.andExpect(jsonPath("$.length()").value(1));

		// direct addresses: a hidden event and its files are not there
		mvc.perform(get("/catcam/event/pet_door/20261101/20261101_120002_0002").cookie(friend)).andExpect(status().isOk());
		mvc.perform(get("/catcam/data/pet_door/20261101/20261101_120002_0002/me_rep_image.jpg").cookie(friend)).andExpect(status().isOk());
		for (String hidden : new String[] {"20261101_120003_0003", "20261101_120004_0004", "20261101_120000_0001",
				"20261101_120005_0005", "20261101_120006_0006"}) {
			mvc.perform(get("/catcam/event/pet_door/20261101/" + hidden).cookie(friend)).andExpect(status().isNotFound());
			mvc.perform(get("/catcam/data/pet_door/20261101/" + hidden + "/me_data.json").cookie(friend)).andExpect(status().isNotFound());
		}
		mvc.perform(get("/catcam/latest/neighbor_cam").cookie(friend)).andExpect(status().isForbidden());

		// view only
		mvc.perform(put("/catcam/event/pet_door/20261101/20261101_120002_0002/classification").cookie(friend)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cat\": true}")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("view only"));

		// the admin still sees everything
		mvc.perform(get("/catcam/latest/pet_door").cookie(boss)).andExpect(jsonPath("$.length()").value(6));
	}

	@Test
	void adminSeesEverythingAndMayDelete() throws Exception {
		Cookie boss = signIn("boss", "admin");
		mvc.perform(get("/catcam/latest/neighbor_cam").cookie(boss)).andExpect(status().isOk());
		mvc.perform(delete("/catcam/event/neighbor_cam/20261101/20261101_120000_0001").cookie(boss)).andExpect(status().isOk());
		mvc.perform(get("/auth/admin/requests").cookie(boss)).andExpect(status().isOk()).andExpect(jsonPath("$.accounts[0].id").value("home"));
	}

	@Test
	void rejectedStaysRejectedAndIsNotAlertedAgain() throws Exception {
		Cookie boss = signIn("boss", "admin");
		signIn("spammer", "pending");
		mvc.perform(post("/auth/admin/reject").cookie(boss).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"spammer@example.com\"}")).andExpect(status().isOk());
		reset(notifier);
		Cookie c = signIn("spammer", "rejected");
		verify(notifier, never()).send(anyString());
		mvc.perform(get("/catcam/megroups").cookie(c)).andExpect(status().isForbidden());
	}

	@Test
	void changeFromAnotherSiteIsRefused() throws Exception {
		Cookie boss = signIn("boss", "admin");
		mvc.perform(delete("/catcam/event/pet_door/20261101/20261101_120000_0001").cookie(boss)
				.header("Origin", "https://evil.example").header("Host", "localhost")).andExpect(status().isForbidden());
	}

	@Test
	void forgedCookieCountsForNothing() throws Exception {
		mvc.perform(get("/catcam/megroups").cookie(new Cookie("cmo_session", "eyJlIjoiYm9zc0BleGFtcGxlLmNvbSJ9.forged")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void signOutClearsTheCookie() throws Exception {
		mvc.perform(post("/auth/logout")).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
		verify(notifier, never()).send(eq("x"));
	}
}
