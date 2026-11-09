package com.mckellar.pimaster.piexp.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Signing in and out, and the admin's approvals. Who may call what is decided in {@link AuthFilter}
 * ({@code /auth/admin/**} is admins only).
 */
@RestController
public class AuthController {

	private static final Logger Log = LoggerFactory.getLogger(AuthController.class);

	private final AuthConfig config;
	private final AccessStore store;
	private final Sessions sessions;
	private final GoogleSignIn google;
	private final AdminNotifier notifier;

	public AuthController(AuthConfig config, AccessStore store, Sessions sessions, GoogleSignIn google, AdminNotifier notifier) {
		this.config = config;
		this.store = store;
		this.sessions = sessions;
		this.google = google;
		this.notifier = notifier;
	}

	/** For the web pages: how this server signs people in, and who is asking (and their status). */
	@GetMapping("/auth/config")
	public Map<String, Object> getConfig(HttpServletRequest request) {
		Map<String, Object> m = new LinkedHashMap<>(config.describe(store));
		m.remove("access");
		m.put("google_client_id", config.isOpen() ? "" : config.googleClientId());
		Caller c = Caller.of(request);
		m.put("caller", c.kind());
		if (c.isPerson()) {
			m.put("email", c.id());
			m.put("name", c.name());
			m.put("status", c.admin() ? "admin" : c.role());    // admin, member, pending, rejected
			m.put("admin", c.admin());
			m.put("view_only", c.viewOnly());
			m.put("animals_only", c.animalsOnly());    // every clip they see is free of people
			m.put("labels", c.access().shownLabels());    // the only labels they can find; null: any
			m.put("access", c.access().describe(store::homeName));
		}
		return m;
	}

	/**
	 * The "Sign in with Google" button's token. Checked with Google, then the person gets the sign-in cookie
	 * whatever their status (so the page can say "waiting for approval"); access is decided per request. A
	 * stranger's first sign-in becomes a request for access and alerts the admin, once.
	 */
	@PostMapping("/auth/google")
	public Map<String, Object> signIn(@RequestBody Map<String, String> body, HttpServletRequest request, HttpServletResponse response) {
		if (config.isOpen()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "this server has no sign-in (open mode)");
		Optional<GoogleSignIn.GoogleUser> user = google.check(body == null ? null : body.get("credential"));
		if (user.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in didn't check out");
		GoogleSignIn.GoogleUser u = user.get();
		sessions.set(request, response, sessions.create(u.email(), u.name()), false);

		String status;
		if (store.isAdmin(u.email())) status = "admin";
		else if (store.accountForEmail(u.email()).isPresent()) status = "member";
		else if (store.isRejected(u.email())) status = "rejected";
		else {
			status = "pending";
			if (store.recordPending(u.email(), u.name()))
				notifier.send("Home Cams: " + u.name() + " (" + u.email() + ") asked for access.\nApprove or reject: "
						+ siteUrl(request) + "/cmo_client/index.html#/access");
		}
		Log.info("signed in: {} ({})", u.email(), status);
		return Map.of("email", u.email(), "name", u.name(), "status", status);
	}

	@PostMapping("/auth/logout")
	public Map<String, Object> signOut(HttpServletRequest request, HttpServletResponse response) {
		sessions.set(request, response, null, true);
		return Map.of("signed_out", true);
	}

	/** For the admin page: who is waiting, the households and their members, and who was turned away. */
	@GetMapping("/auth/admin/requests")
	public Map<String, Object> requests() {
		List<Map<String, Object>> accounts = new ArrayList<>();
		for (AccessStore.Account a : store.accounts())
			accounts.add(Map.of("id", a.id(), "members", a.members(), "cameras", a.cameras(),
					"view_only", a.viewOnly(), "animals_only", a.animalsOnly(),
					"grants", a.grants().stream().map(Access.Grant::toMap).toList(),
					"summary", store.access(a).describe(store::homeName) + (a.viewOnly() ? " · view only" : "")));
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("pending", store.pending());
		List<Map<String, Object>> homes = new ArrayList<>();
		for (AccessStore.Home h : store.homes()) homes.add(Map.of("id", h.id(), "name", store.homeName(h.id()), "cameras", h.cameras()));
		m.put("homes", homes);
		m.put("accounts", accounts);
		m.put("rejected", store.rejected());
		m.put("admins", store.admins());
		m.put("devices", store.devices().stream().map(d -> Map.of("id", d.id(), "account", String.valueOf(d.account()), "role", String.valueOf(d.role()))).toList());
		return m;
	}

	/**
	 * Create a group or change what it may see: {"id": "aidan", "view_only": true, "grants": [{"level": "camera",
	 * "id": "pet_door", "show": ["cat"], "hide": ["person"]}]}. Members stay where they are.
	 */
	@PostMapping("/auth/admin/account")
	@SuppressWarnings("unchecked")
	public Map<String, Object> saveAccount(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		String id = body == null || body.get("id") == null ? "" : String.valueOf(body.get("id")).trim();
		boolean viewOnly = body != null && Boolean.TRUE.equals(body.get("view_only"));
		try {
			List<Access.Grant> grants = new ArrayList<>();
			Object gs = body == null ? null : body.get("grants");
			if (gs != null && !(gs instanceof List)) throw new IllegalArgumentException("grants must be a list");
			for (Object g : gs == null ? List.of() : (List<Object>) gs) {
				if (!(g instanceof Map)) throw new IllegalArgumentException("a grant must be an object");
				grants.add(Access.Grant.fromMap((Map<String, Object>) g));
			}
			store.saveAccount(id, viewOnly, grants);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		Log.info("{} saved group {}", Caller.of(request).id(), id);
		return Map.of("saved", id);
	}

	@PostMapping("/auth/admin/account/delete")
	public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body, HttpServletRequest request) {
		String id = required(body, "id");
		try {
			store.deleteAccount(id);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		Log.info("{} deleted group {}", Caller.of(request).id(), id);
		return Map.of("deleted", id);
	}

	/** Which cameras are in which home: {"homes": [{"id": "mckellar", "name": "McKellar house", "cameras": [...]}]}. */
	@PostMapping("/auth/admin/homes")
	@SuppressWarnings("unchecked")
	public Map<String, Object> saveHomes(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		try {
			Object hs = body == null ? null : body.get("homes");
			if (!(hs instanceof List)) throw new IllegalArgumentException("homes must be a list");
			List<AccessStore.Home> homes = new ArrayList<>();
			for (Object o : (List<Object>) hs) {
				if (!(o instanceof Map)) throw new IllegalArgumentException("a home must be an object");
				Map<String, Object> h = (Map<String, Object>) o;
				Object cams = h.get("cameras");
				List<String> cameras = cams instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
				homes.add(new AccessStore.Home(h.get("id") == null ? null : String.valueOf(h.get("id")).trim(),
						h.get("name") == null ? null : String.valueOf(h.get("name")), cameras));
			}
			store.saveHomes(homes);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		Log.info("{} saved the homes", Caller.of(request).id());
		return Map.of("saved", true);
	}

	@PostMapping("/auth/admin/approve")
	public Map<String, Object> approve(@RequestBody Map<String, String> body, HttpServletRequest request) {
		String email = required(body, "email"), account = required(body, "account");
		if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not an email address: " + email);
		try {
			store.approve(email, account);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		Log.info("{} approved {} into {}", Caller.of(request).id(), email, account);
		return Map.of("approved", email, "account", account);
	}

	@PostMapping("/auth/admin/reject")
	public Map<String, Object> reject(@RequestBody Map<String, String> body, HttpServletRequest request) {
		String email = required(body, "email");
		if (store.isAdmin(email)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "an admin can't be turned away here");
		store.reject(email);
		Log.info("{} rejected {}", Caller.of(request).id(), email);
		return Map.of("rejected", email);
	}

	private static String required(Map<String, String> body, String key) {
		String v = body == null ? null : body.get(key);
		if (v == null || v.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
		return v.trim();
	}

	/** https://host as the browser reached us (Caddy passes the scheme in X-Forwarded-Proto). */
	private static String siteUrl(HttpServletRequest request) {
		String host = request.getHeader("Host");
		return (Sessions.isHttps(request) ? "https" : "http") + "://" + (host == null ? request.getServerName() : host);
	}
}
