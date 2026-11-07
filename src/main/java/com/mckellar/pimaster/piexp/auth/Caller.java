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
 * @param viewOnly    a person whose household may look but not change labels
 * @param animalsOnly sees only events labeled cat, dog or raccoon and not person (see {@link #mayView})
 */
public record Caller(String kind, String id, String account, String role, Set<String> cameras, boolean admin, String name,
		boolean viewOnly, boolean animalsOnly) {

	public Caller(String kind, String id, String account, String role, Set<String> cameras, boolean admin, String name) {
		this(kind, id, account, role, cameras, admin, name, false, false);
	}

	/** The labels an animals-only caller may see; one of them must be true. */
	public static final Set<String> ANIMALS = Set.of("cat", "dog", "raccoon");

	/** Labels that mean a person (or part of one) is in the clip; none may be true for an animals-only caller. */
	public static final Set<String> PEOPLE = Set.of("person", "legs", "delivery");

	/**
	 * May this caller see an event with these labels (its me_ollama.json, or null if it has none)? Always yes,
	 * except for animals-only callers (the friends household): then "person" must be false (not just missing),
	 * no other people label (legs, delivery) may be true, and an animal must be true. So an event the AI never
	 * classified, or one with any part of a person in it, stays hidden.
	 */
	public boolean mayView(java.util.Map<String, Object> labels) {
		if (!animalsOnly) return true;
		if (labels == null) return false;
		Boolean person = null;
		boolean animal = false, people = false;
		for (java.util.Map.Entry<String, Object> e : labels.entrySet()) {
			String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
			if (k.equals("person") && e.getValue() instanceof Boolean b) person = b;
			if (PEOPLE.contains(k) && Boolean.TRUE.equals(e.getValue())) people = true;
			if (ANIMALS.contains(k) && Boolean.TRUE.equals(e.getValue())) animal = true;
		}
		return Boolean.FALSE.equals(person) && !people && animal;
	}

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
