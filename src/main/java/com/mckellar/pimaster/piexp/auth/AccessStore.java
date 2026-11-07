package com.mckellar.pimaster.piexp.auth;

import java.util.List;
import java.util.Optional;

/**
 * Who may use the server: accounts (households) with their members and cameras, the devices (cameras and
 * scripts) with their keys, the admins, and the people waiting for approval. The rest of the code uses only
 * this interface, so the JSON file behind it ({@link JsonAccessStore}) can be replaced by a database later.
 */
public interface AccessStore {

	/**
	 * An access group (a household, or e.g. "friends"): the people (verified emails) in it and what they may see.
	 * {@code cameras} is the old way of saying which cameras; {@code grants} is everything it may see, on the
	 * world / home / camera / event-type levels (see {@link Access}), with the cameras included. {@code viewOnly}:
	 * its people may look but not change labels. {@code animalsOnly}: the event-type rule for grants that don't
	 * set their own (only cats, dogs and raccoons, never a clip with a person in it).
	 */
	record Account(String id, List<String> members, List<String> cameras, boolean viewOnly, boolean animalsOnly,
			List<Access.Grant> grants) {
		public Account(String id, List<String> members, List<String> cameras) {
			this(id, members, cameras, false, false,
					cameras.stream().map(c -> new Access.Grant(Access.Level.CAMERA, c, Access.Rule.EVERYTHING)).toList());
		}
	}

	/** A home: its cameras. A camera not in any home is in {@link #DEFAULT_HOME}. */
	record Home(String id, String name, List<String> cameras) {
	}

	/** The home of cameras that aren't listed in one (so a server with no homes set up is one home). */
	String DEFAULT_HOME = "home";

	List<Home> homes();

	/** The home this camera is in ({@link #DEFAULT_HOME} if it isn't listed in one). */
	default String homeOf(String camera) {
		for (Home h : homes()) if (h.cameras().contains(camera)) return h.id();
		return DEFAULT_HOME;
	}

	/** A home's name for people ("Home" for the default one). */
	default String homeName(String id) {
		for (Home h : homes()) if (h.id().equals(id)) return h.name() == null || h.name().isBlank() ? h.id() : h.name();
		return DEFAULT_HOME.equals(id) ? "Home" : id;
	}

	/** What a group's people (and reader devices) may see. */
	default Access access(Account a) {
		return new Access(a.grants(), this::homeOf);
	}

	/**
	 * A camera or a script. {@code role} is "camera" (may upload to its own cameras and read health) or
	 * "reader" (may read its account's cameras, e.g. test scripts downloading clips).
	 */
	record Device(String id, String account, String role, List<String> cameras) {
		public boolean isCamera() {
			return "camera".equals(role);
		}

		public boolean isReader() {
			return "reader".equals(role);
		}
	}

	/** Someone who signed in with Google and is waiting for an admin to let them in. */
	record Pending(String email, String name, String firstSeen) {
	}

	/** The device whose key this is, if any. Keys are stored only as hashes. */
	Optional<Device> deviceForKey(String key);

	/** The account a device or a person belongs to. */
	Optional<Account> account(String id);

	/** The account whose members include this (verified) email, if any. */
	Optional<Account> accountForEmail(String email);

	List<Account> accounts();

	/** Admins approve people and may see every camera and delete events. */
	boolean isAdmin(String email);

	List<Pending> pending();

	List<String> rejected();

	boolean isRejected(String email);

	/**
	 * Note that this person asked for access. True only the first time (so the admin is alerted once); false if
	 * they are already waiting, a member, an admin, rejected, or too many people are already waiting.
	 */
	boolean recordPending(String email, String name);

	/** Let a waiting (or rejected) person in, as a member of this account. */
	void approve(String email, String accountId);

	/**
	 * Turn a person away: someone waiting, or a member losing access (taken out of their household). They stay
	 * turned away (and aren't alerted about again) until let in again.
	 */
	void reject(String email);

	/** How many devices and accounts are configured, for health and startup logs. */
	String summary();
}
