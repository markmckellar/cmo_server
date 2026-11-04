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
				+ "  {\"id\": \"neighbor\", \"members\": [], \"cameras\": [\"neighbor_cam\"]}]}");
		reset(notifier);
		for (String who : new String[] {"boss", "newbie", "spammer"})
			when(google.check("token-" + who)).thenReturn(Optional.of(new GoogleSignIn.GoogleUser(who + "@example.com", who, "sub-" + who)));
		when(google.check("bad-token")).thenReturn(Optional.empty());
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
