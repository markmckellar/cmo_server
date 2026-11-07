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
	 * A household: the people (verified emails) and the cameras that belong to it. {@code viewOnly}: its people
	 * may look but not change labels. {@code animalsOnly}: its people see only events labeled cat, dog or raccoon
	 * and not person (e.g. a "friends" household for people Mark shares the link with).
	 */
	record Account(String id, List<String> members, List<String> cameras, boolean viewOnly, boolean animalsOnly) {
		public Account(String id, List<String> members, List<String> cameras) {
			this(id, members, cameras, false, false);
		}
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

	/** Turn a waiting person away; they stay turned away (and aren't alerted about again). */
	void reject(String email);

	/** How many devices and accounts are configured, for health and startup logs. */
	String summary();
}
