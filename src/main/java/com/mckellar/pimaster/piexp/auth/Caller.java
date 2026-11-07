package com.mckellar.pimaster.piexp.auth;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Who is making a request, as worked out by {@link AuthFilter} and left on the request for the controllers.
 *
 * @param kind     "device", "person", "local" (open mode: anyone on the home network) or "anonymous"
 * @param id       the device id or the person's email; null for local and anonymous
 * @param account  the access group (household) id; null if none
 * @param role     a device's role ("camera" or "reader"); a person's: "member", "pending" or "rejected"
 * @param access   what the caller may see: world, home, camera and event-type grants (see {@link Access})
 * @param admin    may approve people, see every camera and delete events
 * @param name     a person's display name (from Google), else null
 * @param viewOnly may look but not change labels
 */
public record Caller(String kind, String id, String account, String role, Access access, boolean admin, String name,
		boolean viewOnly) {

	public Caller {
		access = access == null ? Access.NOTHING : access;
	}

	public static final String ATTRIBUTE = Caller.class.getName();

	public static final Caller LOCAL = new Caller("local", null, null, null, Access.EVERYTHING, true, null, false);
	public static final Caller ANONYMOUS = new Caller("anonymous", null, null, null, Access.NOTHING, false, null, false);

	/** The caller the filter left on this request; anonymous if there is none (e.g. in unit tests of controllers). */
	public static Caller of(HttpServletRequest request) {
		Object c = request.getAttribute(ATTRIBUTE);
		return c instanceof Caller caller ? caller : ANONYMOUS;
	}

	/** May this caller see this camera (some of its clips)? */
	public boolean canSee(String camera) {
		return access.canSee(camera);
	}

	/** May this caller see this clip: on this camera, with these labels (its me_ollama.json, or null)? */
	public boolean mayView(String camera, Map<String, Object> labels) {
		return access.mayView(camera, labels);
	}

	/** Does deciding about this camera's clips need their labels? If not, they needn't be read. */
	public boolean needsLabels(String camera) {
		return access.needsLabels(camera);
	}

	/** Only clips without people, everywhere this caller looks (e.g. the friends household). */
	public boolean animalsOnly() {
		return access.hidesPeople();
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
