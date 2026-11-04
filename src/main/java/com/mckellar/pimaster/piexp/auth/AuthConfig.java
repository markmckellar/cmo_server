package com.mckellar.pimaster.piexp.auth;

import java.net.InetAddress;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth settings and beans:
 * <ul>
 * <li>{@code cmo.auth.mode}: {@code google} (the default) or {@code open} (home network only, see {@link AuthFilter})
 * <li>{@code cmo.auth.public-read}: temporary, viewing without a login (default false)
 * <li>{@code cmo.auth.access-file}: accounts, admins, device keys, people waiting (default {@code local/access.json})
 * <li>{@code cmo.auth.google-client-id}: the "Home Cams web" OAuth client ID from the Google console
 * <li>{@code cmo.auth.session-key-file}: the secret that signs sign-in cookies, made on first start (default
 * {@code local/session.key}); {@code cmo.auth.session-days}: how long a sign-in lasts (default 30)
 * <li>{@code cmo.auth.notify-config}: TOKEN=/CHAT_ID= Telegram config for "someone wants access" alerts (default none)
 * </ul>
 * In open mode the server refuses to start if {@code server.address} is a specific address outside the home
 * network; the filter also refuses any request that isn't from the home network.
 */
@Configuration
public class AuthConfig {

	private static final Logger Log = LoggerFactory.getLogger(AuthConfig.class);

	private final String mode;
	private final boolean publicRead;
	private final String accessFile;
	private String googleClientId = "";
	private String sessionKeyFile = "local/session.key";
	private int sessionDays = 30;
	private String notifyConfig = "";

	public AuthConfig(@Value("${cmo.auth.mode:google}") String mode,
			@Value("${cmo.auth.public-read:false}") boolean publicRead,
			@Value("${cmo.auth.access-file:local/access.json}") String accessFile,
			@Value("${server.address:}") String serverAddress) {
		this.mode = mode.trim().toLowerCase();
		this.publicRead = publicRead;
		this.accessFile = accessFile;
		if (!this.mode.equals("google") && !this.mode.equals("open"))
			throw new IllegalStateException("cmo.auth.mode must be google or open, not " + mode);
		if (isOpen()) {
			String a = serverAddress.trim();
			boolean anyAddress = a.isEmpty() || a.equals("0.0.0.0") || a.equals("::");
			if (!anyAddress && !AuthFilter.isHomeAddress(literal(a)))
				throw new IllegalStateException("cmo.auth.mode=open only on a home-network address, not server.address=" + a);
			Log.warn("cmo.auth.mode=open: no logins; only requests from the home network are served");
		}
		else {
			Log.info("cmo.auth.mode=google{}", publicRead ? " (public-read: anyone may view)" : "");
		}
	}

	@Value("${cmo.auth.google-client-id:}")
	void setGoogleClientId(String id) {
		this.googleClientId = id.trim();
	}

	@Value("${cmo.auth.session-key-file:local/session.key}")
	void setSessionKeyFile(String f) {
		this.sessionKeyFile = f;
	}

	@Value("${cmo.auth.session-days:30}")
	void setSessionDays(int d) {
		this.sessionDays = d;
	}

	@Value("${cmo.auth.notify-config:}")
	void setNotifyConfig(String f) {
		this.notifyConfig = f.trim();
	}

	private static String literal(String host) {
		try {
			return InetAddress.getByName(host).getHostAddress();
		}
		catch (Exception e) {
			return host;
		}
	}

	public boolean isOpen() {
		return mode.equals("open");
	}

	public boolean isPublicRead() {
		return publicRead;
	}

	public String googleClientId() {
		return googleClientId;
	}

	/** What /auth/config and /catcam/health report. */
	public Map<String, Object> describe(AccessStore store) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("mode", mode);
		m.put("public_read", !isOpen() && publicRead);
		m.put("google_sign_in", !isOpen() && !googleClientId.isEmpty());
		m.put("access", store.summary());
		return m;
	}

	@Bean
	public AccessStore accessStore() {
		return new JsonAccessStore(Paths.get(accessFile));
	}

	@Bean
	public Sessions sessions() {
		if (isOpen()) {    // nobody signs in; a throwaway key, no file
			byte[] k = new byte[32];
			new java.security.SecureRandom().nextBytes(k);
			return new Sessions(k, Duration.ofDays(sessionDays));
		}
		return new Sessions(Paths.get(sessionKeyFile), Duration.ofDays(sessionDays));
	}

	@Bean
	public GoogleSignIn googleSignIn() {
		if (isOpen() || googleClientId.isEmpty()) {
			if (!isOpen()) Log.warn("cmo.auth.google-client-id is not set: nobody can sign in with Google");
			return credential -> java.util.Optional.empty();
		}
		return new GoogleSignIn.Real(googleClientId);
	}

	@Bean
	public AdminNotifier adminNotifier() {
		return new AdminNotifier(notifyConfig.isEmpty() ? null : Paths.get(notifyConfig));
	}

	@Bean
	public FilterRegistrationBean<AuthFilter> authFilter(AccessStore store) {
		FilterRegistrationBean<AuthFilter> reg = new FilterRegistrationBean<>(new AuthFilter(store, sessions(), isOpen(), publicRead));
		reg.addUrlPatterns("/*");
		reg.setName("cmoAuthFilter");
		return reg;
	}
}
