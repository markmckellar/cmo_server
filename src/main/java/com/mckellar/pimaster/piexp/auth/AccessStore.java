package com.mckellar.pimaster.piexp.auth;

import java.util.List;
import java.util.Optional;

/**
 * Who may use the server: accounts (households) with their members and cameras, and the devices (cameras and
 * scripts) with their keys. The rest of the code uses only this interface, so the JSON file behind it
 * ({@link JsonAccessStore}) can be replaced by a database later.
 */
public interface AccessStore {

	/** A household: the people (verified emails) and the cameras that belong to it. */
	record Account(String id, List<String> members, List<String> cameras) {
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

	/** The device whose key this is, if any. Keys are stored only as hashes. */
	Optional<Device> deviceForKey(String key);

	/** The account a device or a person belongs to. */
	Optional<Account> account(String id);

	/** The account whose members include this (verified) email, if any. */
	Optional<Account> accountForEmail(String email);

	/** How many devices and accounts are configured, for health and startup logs. */
	String summary();
}
