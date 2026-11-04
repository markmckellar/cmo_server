package com.mckellar.pimaster.piexp.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * {@link AccessStore} backed by a JSON file (by default {@code local/access.json}, never in git):
 *
 * <pre>
 * {
 *   "accounts": [ {"id": "mckellar", "members": ["someone@gmail.com"], "cameras": ["pet_door", "porch_cam"]} ],
 *   "devices":  [ {"id": "pet_door_pi", "account": "mckellar", "role": "camera", "cameras": ["pet_door"],
 *                  "key_hashes": ["sha256:..."]} ]
 * }
 * </pre>
 *
 * A device may have two key hashes, so its key can be changed without downtime (add the new one, update the
 * device, remove the old one). The file is read again whenever it changes, so adding a device needs no
 * restart. A file that doesn't parse leaves the last good one in use.
 */
public class JsonAccessStore implements AccessStore {

	private static final Logger Log = LoggerFactory.getLogger(JsonAccessStore.class);

	private static class FileAccount {
		String id;
		List<String> members = new ArrayList<>();
		List<String> cameras = new ArrayList<>();
	}

	private static class FileDevice {
		String id;
		String account;
		String role;
		List<String> cameras = new ArrayList<>();
		@SerializedName("key_hashes")
		List<String> keyHashes = new ArrayList<>();
	}

	private static class FileContents {
		List<FileAccount> accounts = new ArrayList<>();
		List<FileDevice> devices = new ArrayList<>();
	}

	private final Path file;
	private FileContents contents = new FileContents();
	private long loadedModified = Long.MIN_VALUE;

	public JsonAccessStore(Path file) {
		this.file = file;
		reloadIfChanged();
	}

	/** "sha256:" + the hex SHA-256 of the key, the form stored in the file. */
	public static String hashKey(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return "sha256:" + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private synchronized FileContents current() {
		reloadIfChanged();
		return contents;
	}

	private void reloadIfChanged() {
		long modified;
		try {
			modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
		}
		catch (IOException e) {
			Log.error("access file {}: {}", file, e.toString());
			return;
		}
		if (modified == loadedModified) return;
		loadedModified = modified;
		if (modified == -1) {
			contents = new FileContents();
			Log.warn("access file {} not found: no devices or accounts", file);
			return;
		}
		try {
			FileContents read = new Gson().fromJson(Files.readString(file), FileContents.class);
			if (read == null) read = new FileContents();
			if (read.accounts == null) read.accounts = new ArrayList<>();
			if (read.devices == null) read.devices = new ArrayList<>();
			contents = read;
			Log.info("access file {} loaded: {}", file, summary(read));
		}
		catch (Exception e) {
			Log.error("access file {} not loaded, keeping the previous one: {}", file, e.toString());
		}
	}

	@Override
	public Optional<Device> deviceForKey(String key) {
		if (key == null || key.isBlank()) return Optional.empty();
		byte[] hash = hashKey(key.trim()).getBytes(StandardCharsets.UTF_8);
		for (FileDevice d : current().devices) {
			for (String stored : d.keyHashes == null ? List.<String>of() : d.keyHashes) {
				if (stored != null && MessageDigest.isEqual(hash, stored.trim().getBytes(StandardCharsets.UTF_8)))
					return Optional.of(new Device(d.id, d.account, d.role, listOf(d.cameras)));
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<Account> account(String id) {
		if (id == null) return Optional.empty();
		for (FileAccount a : current().accounts)
			if (id.equals(a.id)) return Optional.of(toAccount(a));
		return Optional.empty();
	}

	@Override
	public Optional<Account> accountForEmail(String email) {
		if (email == null) return Optional.empty();
		String wanted = email.trim().toLowerCase(Locale.ROOT);
		for (FileAccount a : current().accounts)
			for (String m : listOf(a.members))
				if (m != null && wanted.equals(m.trim().toLowerCase(Locale.ROOT))) return Optional.of(toAccount(a));
		return Optional.empty();
	}

	@Override
	public String summary() {
		return summary(current());
	}

	private static String summary(FileContents c) {
		return c.accounts.size() + " account(s), " + c.devices.size() + " device(s)";
	}

	private static Account toAccount(FileAccount a) {
		return new Account(a.id, listOf(a.members), listOf(a.cameras));
	}

	private static List<String> listOf(List<String> l) {
		return l == null ? List.of() : List.copyOf(l);
	}
}
