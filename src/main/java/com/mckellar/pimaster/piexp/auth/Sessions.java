package com.mckellar.pimaster.piexp.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The sign-in cookie: who signed in (email, name) and until when, signed with a secret only this server
 * has, so it can't be forged or changed. Nothing is stored server-side, so a restart keeps everyone signed
 * in. Whether the person may see anything is decided on every request from access.json, so removing someone
 * there takes effect at once, cookie or not.
 *
 * <p>The cookie is HttpOnly (page scripts can't read it), SameSite=Lax (other sites can't make the browser send
 * it with a change request) and Secure when the site is reached over HTTPS.
 */
public class Sessions {

	private static final Logger Log = LoggerFactory.getLogger(Sessions.class);

	public static final String COOKIE = "cmo_session";

	/** Who a valid cookie says signed in. */
	public record Session(String email, String name) {
	}

	private final byte[] key;
	private final Duration lifetime;

	public Sessions(Path keyFile, Duration lifetime) {
		this.key = loadOrCreateKey(keyFile);
		this.lifetime = lifetime;
	}

	public Sessions(byte[] key, Duration lifetime) {    // open mode and tests
		this.key = key;
		this.lifetime = lifetime;
	}

	private static byte[] loadOrCreateKey(Path file) {
		try {
			if (Files.exists(file)) {
				byte[] k = Base64.getDecoder().decode(Files.readString(file).trim());
				if (k.length >= 32) return k;
				throw new IllegalStateException(file + " holds a key shorter than 32 bytes");
			}
			byte[] k = new byte[32];
			new SecureRandom().nextBytes(k);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(file, Base64.getEncoder().encodeToString(k) + "\n");
			try {
				Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
			}
			catch (UnsupportedOperationException ignored) {
			}
			Log.info("made a new session key in {}", file);
			return k;
		}
		catch (IOException e) {
			throw new IllegalStateException("session key file " + file + ": " + e, e);
		}
	}

	/** The cookie value for this person, valid for the session lifetime from now. */
	public String create(String email, String name) {
		String body = Base64.getUrlEncoder().withoutPadding().encodeToString(new Gson().toJson(
				Map.of("e", email, "n", name == null ? email : name, "x", Instant.now().plus(lifetime).getEpochSecond()))
				.getBytes(StandardCharsets.UTF_8));
		return body + "." + sign(body);
	}

	/** Who this cookie value says signed in, if it is genuine and not expired. */
	public Optional<Session> read(String value) {
		if (value == null) return Optional.empty();
		int dot = value.indexOf('.');
		if (dot <= 0) return Optional.empty();
		String body = value.substring(0, dot);
		if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8),
				value.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) return Optional.empty();
		try {
			Map<?, ?> m = new Gson().fromJson(new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8), Map.class);
			double expires = ((Number) m.get("x")).doubleValue();
			if (Instant.now().getEpochSecond() > expires) return Optional.empty();
			return Optional.of(new Session((String) m.get("e"), (String) m.get("n")));
		}
		catch (Exception e) {
			return Optional.empty();
		}
	}

	public Optional<Session> read(HttpServletRequest request) {
		if (request.getCookies() == null) return Optional.empty();
		for (Cookie c : request.getCookies())
			if (COOKIE.equals(c.getName())) return read(c.getValue());
		return Optional.empty();
	}

	public void set(HttpServletRequest request, HttpServletResponse response, String value, boolean clear) {
		StringBuilder h = new StringBuilder(COOKIE).append('=').append(clear ? "" : value)
				.append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(clear ? 0 : lifetime.getSeconds());
		if (isHttps(request)) h.append("; Secure");
		response.addHeader("Set-Cookie", h.toString());
	}

	/** Reached over HTTPS, directly or through Caddy (which says so in X-Forwarded-Proto). */
	public static boolean isHttps(HttpServletRequest request) {
		return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
	}

	private String sign(String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
