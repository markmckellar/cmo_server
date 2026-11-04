package com.mckellar.pimaster.piexp.auth;

import java.net.InetAddress;
import java.nio.file.Paths;
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
 * <li>{@code cmo.auth.public-read}: temporary, viewing without a login until Google sign-in exists (default false)
 * <li>{@code cmo.auth.access-file}: the accounts and device keys (default {@code local/access.json})
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
			Log.info("cmo.auth.mode=google{}", publicRead ? " (public-read: viewing open until Google sign-in exists)" : "");
		}
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

	/** What /auth/config and /catcam/health report. */
	public Map<String, Object> describe(AccessStore store) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("mode", mode);
		m.put("public_read", !isOpen() && publicRead);
		m.put("access", store.summary());
		return m;
	}

	@Bean
	public AccessStore accessStore() {
		return new JsonAccessStore(Paths.get(accessFile));
	}

	@Bean
	public FilterRegistrationBean<AuthFilter> authFilter(AccessStore store) {
		FilterRegistrationBean<AuthFilter> reg = new FilterRegistrationBean<>(new AuthFilter(store, isOpen(), publicRead));
		reg.addUrlPatterns("/*");
		reg.setName("cmoAuthFilter");
		return reg;
	}
}
