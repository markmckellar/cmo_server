package com.mckellar.pimaster.piexp.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.google.gson.Gson;

/**
 * The one place that decides who may do what. Every request goes through here: any credential is turned into a
 * {@link Caller}, which is checked against the request's path and left on the request for the controllers.
 *
 * <p>Modes ({@code cmo.auth.mode}):
 * <ul>
 * <li>{@code google} (the default): cameras and scripts send {@code Authorization: Bearer <device key>}; people
 * will sign in with Google (not built yet). A camera may only upload to its own cameras; a reader device may read
 * its account's cameras. Viewing without a login is allowed only while {@code cmo.auth.public-read=true}, a
 * temporary setting until Google sign-in exists.
 * <li>{@code open}: for a server at home (e.g. ada's copy). Anyone on the home network may do anything; keys are
 * ignored. Requests from anywhere else, or that came through a proxy, are refused, so an open server behind
 * Caddy or a forwarded port can't be used from the internet.
 * </ul>
 * Always public: {@code /}, {@code /catcam/version}, {@code /catcam/health}, {@code /auth/config} and the web app's
 * own files under {@code /cmo_client/}.
 */
public class AuthFilter extends OncePerRequestFilter {

	private static final Logger Log = LoggerFactory.getLogger(AuthFilter.class);

	private final AccessStore store;
	private final boolean open;
	private final boolean publicRead;

	public AuthFilter(AccessStore store, boolean open, boolean publicRead) {
		this.store = store;
		this.open = open;
		this.publicRead = publicRead;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String method = request.getMethod();
		String path = request.getRequestURI().substring(request.getContextPath().length());

		if ("OPTIONS".equals(method)) {    // CORS preflight; the real request is checked
			chain.doFilter(request, response);
			return;
		}

		if (open) {
			if (!isHomeNetwork(request)) {
				deny(response, 403, "this server is in open mode and only serves the home network");
				return;
			}
			request.setAttribute(Caller.ATTRIBUTE, Caller.LOCAL);
			chain.doFilter(request, response);
			return;
		}

		Caller caller;
		String key = bearerKey(request);
		if (key != null) {
			Optional<Caller> found = callerForKey(key);
			if (found.isEmpty()) {
				Log.warn("unknown device key from {} for {} {}", request.getRemoteAddr(), method, path);
				deny(response, 401, "unknown device key");
				return;
			}
			caller = found.get();
		}
		else {
			caller = publicRead ? new Caller("anonymous", null, null, null, null) : Caller.ANONYMOUS;
		}
		request.setAttribute(Caller.ATTRIBUTE, caller);

		String problem = check(caller, method, path);
		if (problem == null) {
			chain.doFilter(request, response);
			return;
		}
		boolean anonymous = "anonymous".equals(caller.kind());
		if (anonymous) response.setHeader("WWW-Authenticate", "Bearer");
		Log.info("refused {} {} for {} {}: {}", method, path, caller.kind(), caller.id(), problem);
		deny(response, anonymous ? 401 : 403, problem);
	}

	/** Null if the caller may make this request, else why not. */
	String check(Caller caller, String method, String path) {
		if (isPublic(method, path)) return null;

		String[] seg = path.split("/");    // "", "catcam", "event", group, ...
		String camera = seg.length > 2 && "uploadme".equals(seg[1]) ? seg[2] : seg.length > 3 ? seg[3] : null;

		if ("POST".equals(method) && path.startsWith("/uploadme/")) {
			if (!caller.isCameraDevice()) return "only camera devices may upload";
			return caller.canSee(camera) ? null : "this device may not upload to " + camera;
		}

		boolean eventPath = path.startsWith("/catcam/event/");
		if (eventPath && ("DELETE".equals(method) || "PUT".equals(method))) {
			if (!caller.isPerson()) return "only people may change or delete events";
			return caller.canSee(camera) ? null : "not your camera";
		}

		if ("GET".equals(method) && isRead(path)) {
			if (caller.isCameraDevice()) return "camera devices may only upload";
			boolean list = path.equals("/catcam/megroups") || path.equals("/catcam/latest");
			if (list) return "anonymous".equals(caller.kind()) && caller.cameras() != null && caller.cameras().isEmpty()
					? "sign in or send a device key" : null;    // the controller lists only what the caller may see
			if (camera == null || camera.isEmpty()) return "no camera in the path";
			if (caller.canSee(camera)) return null;
			return "anonymous".equals(caller.kind()) ? "sign in or send a device key" : "not your camera";
		}

		return "not allowed";
	}

	private static boolean isPublic(String method, String path) {
		if (!"GET".equals(method) && !"HEAD".equals(method)) return false;
		return path.equals("/") || path.equals("/catcam/version") || path.equals("/catcam/health")
				|| path.equals("/auth/config") || path.equals("/favicon.ico") || path.startsWith("/cmo_client/");
	}

	private static boolean isRead(String path) {
		return path.equals("/catcam/megroups") || path.equals("/catcam/latest") || path.startsWith("/catcam/latest/")
				|| path.startsWith("/catcam/meeventgroup/") || path.startsWith("/catcam/search/")
				|| path.startsWith("/catcam/event/") || path.startsWith("/catcam/data/");
	}

	private Optional<Caller> callerForKey(String key) {
		return store.deviceForKey(key).map(d -> {
			Set<String> cameras = new HashSet<>();
			if (d.isCamera()) cameras.addAll(d.cameras());
			else store.account(d.account()).ifPresent(a -> cameras.addAll(a.cameras()));
			return new Caller("device", d.id(), d.account(), d.role(), Set.copyOf(cameras));
		});
	}

	private static String bearerKey(HttpServletRequest request) {
		String h = request.getHeader("Authorization");
		if (h == null || !h.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
		String key = h.substring(7).trim();
		return key.isEmpty() ? null : key;
	}

	/** From this machine or the home network, and not passed on by a proxy (Caddy adds X-Forwarded-For). */
	static boolean isHomeNetwork(HttpServletRequest request) {
		if (request.getHeader("X-Forwarded-For") != null || request.getHeader("Forwarded") != null
				|| request.getHeader("X-Real-IP") != null) return false;
		return isHomeAddress(request.getRemoteAddr());
	}

	static boolean isHomeAddress(String address) {
		if (address == null) return false;
		try {
			InetAddress a = InetAddress.getByName(address);    // a literal address: no lookup
			if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
			byte[] b = a.getAddress();
			return b.length == 16 && (b[0] & 0xfe) == 0xfc;    // IPv6 unique local, fc00::/7
		}
		catch (UnknownHostException e) {
			return false;
		}
	}

	private static void deny(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(new Gson().toJson(java.util.Map.of("error", message)));
	}
}
