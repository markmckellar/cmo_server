package com.mckellar.pimaster.piexp.auth;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Who is making a request, as worked out by {@link AuthFilter} and left on the request for the controllers.
 *
 * @param kind     "device", "person", "local" (open mode: anyone on the home network) or "anonymous"
 * @param id       the device id or the person's email; null for local and anonymous
 * @param account  the account (household) id; null if none
 * @param role     a device's role ("camera" or "reader"); a person's: "member", "pending" or "rejected"
 * @param cameras  the cameras this caller may see; null means every camera
 * @param admin    may approve people, see every camera and delete events
 * @param name     a person's display name (from Google), else null
 */
public record Caller(String kind, String id, String account, String role, Set<String> cameras, boolean admin, String name) {

	public static final String ATTRIBUTE = Caller.class.getName();

	public static final Caller LOCAL = new Caller("local", null, null, null, null, true, null);
	public static final Caller ANONYMOUS = new Caller("anonymous", null, null, null, Set.of(), false, null);

	/** The caller the filter left on this request; anonymous if there is none (e.g. in unit tests of controllers). */
	public static Caller of(HttpServletRequest request) {
		Object c = request.getAttribute(ATTRIBUTE);
		return c instanceof Caller caller ? caller : ANONYMOUS;
	}

	/** May this caller see this camera's events? */
	public boolean canSee(String camera) {
		return cameras == null || cameras.contains(camera);
	}

	public boolean isLocal() {
		return "local".equals(kind);
	}

	public boolean isAnonymous() {
		return "anonymous".equals(kind);
	}

	public boolean isCameraDevice() {
		return "device".equals(kind) && "camera".equals(role);
	}

	public boolean isPerson() {
		return "person".equals(kind);
	}

	/** A person who has been let in (a household member or an admin). */
	public boolean isApprovedPerson() {
		return isPerson() && ("member".equals(role) || admin);
	}
}
