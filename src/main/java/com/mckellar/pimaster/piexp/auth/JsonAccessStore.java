package com.mckellar.pimaster.piexp.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/**
 * {@link AccessStore} backed by a JSON file (by default {@code local/access.json}, never in git, mode 600):
 *
 * <pre>
 * {
 *   "admins":   ["someone@gmail.com"],
 *   "accounts": [ {"id": "home", "members": ["someone@gmail.com"], "cameras": ["pet_door", "porch_cam"]} ],
 *   "devices":  [ {"id": "pet_door_pi", "account": "home", "role": "camera", "cameras": ["pet_door"],
 *                  "key_hashes": ["sha256:..."]} ],
 *   "pending":  [ {"email": "new@gmail.com", "name": "New Person", "first_seen": "2026-11-04T01:00:00Z"} ],
 *   "rejected": ["spam@gmail.com"]
 * }
 * </pre>
 *
 * A device may have two key hashes, so its key can be changed without downtime. The file is read again
 * whenever it changes (hand edits need no restart); a file that doesn't parse leaves the last good one in use.
 * Approvals and sign-up requests are written back safely: a new file, then a rename over the old one.
 */
public class JsonAccessStore implements AccessStore {

	private static final Logger Log = LoggerFactory.getLogger(JsonAccessStore.class);

	/** At most this many people wait at once, so a flood of sign-ups can't grow the file or the alerts without end. */
	static final int MAX_PENDING = 50;

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

	private static class FilePending {
		String email;
		String name;
		@SerializedName("first_seen")
		String firstSeen;
	}

	private static class FileContents {
		List<String> admins = new ArrayList<>();
		List<FileAccount> accounts = new ArrayList<>();
		List<FileDevice> devices = new ArrayList<>();
		List<FilePending> pending = new ArrayList<>();
		List<String> rejected = new ArrayList<>();
	}

	private final Path file;
	private FileContents contents = new FileContents();
	private long loadedModified = Long.MIN_VALUE;

	public JsonAccessStore(Path file) {
		this.file = file;
		synchronized (this) {
			reloadIfChanged();
		}
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

	private static String norm(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean hasEmail(List<String> list, String email) {
		String e = norm(email);
		for (String x : list) if (norm(x).equals(e)) return true;
		return false;
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
			if (read.admins == null) read.admins = new ArrayList<>();
			if (read.accounts == null) read.accounts = new ArrayList<>();
			if (read.devices == null) read.devices = new ArrayList<>();
			if (read.pending == null) read.pending = new ArrayList<>();
			if (read.rejected == null) read.rejected = new ArrayList<>();
			for (FileAccount a : read.accounts) if (a.members == null) a.members = new ArrayList<>();
			contents = read;
			Log.info("access file {} loaded: {}", file, summary(read));
		}
		catch (Exception e) {
			Log.error("access file {} not loaded, keeping the previous one: {}", file, e.toString());
		}
	}

	/** Write the current contents back: a temporary file next to it, then an atomic rename. */
	private void save() {
		try {
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			Path tmp = Files.createTempFile(dir, ".access", ".json");
			try {
				Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
			}
			catch (UnsupportedOperationException ignored) {
			}
			Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(contents) + "\n");
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			loadedModified = Files.getLastModifiedTime(file).toMillis();
		}
		catch (IOException e) {
			throw new IllegalStateException("couldn't save " + file + ": " + e, e);
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
		for (FileAccount a : current().accounts)
			if (hasEmail(a.members, email)) return Optional.of(toAccount(a));
		return Optional.empty();
	}

	@Override
	public List<Account> accounts() {
		return current().accounts.stream().map(JsonAccessStore::toAccount).toList();
	}

	@Override
	public boolean isAdmin(String email) {
		return email != null && hasEmail(current().admins, email);
	}

	@Override
	public List<Pending> pending() {
		return current().pending.stream().map(p -> new Pending(p.email, p.name, p.firstSeen)).toList();
	}

	@Override
	public List<String> rejected() {
		return List.copyOf(current().rejected);
	}

	@Override
	public boolean isRejected(String email) {
		return hasEmail(current().rejected, email);
	}

	@Override
	public synchronized boolean recordPending(String email, String name) {
		FileContents c = current();
		if (isAdmin(email) || accountForEmail(email).isPresent() || hasEmail(c.rejected, email)) return false;
		for (FilePending p : c.pending) if (norm(p.email).equals(norm(email))) return false;
		if (c.pending.size() >= MAX_PENDING) {
			Log.warn("sign-up from {} not recorded: {} people already waiting", email, c.pending.size());
			return false;
		}
		FilePending p = new FilePending();
		p.email = norm(email);
		p.name = name;
		p.firstSeen = Instant.now().toString();
		c.pending.add(p);
		save();
		Log.info("{} ({}) asked for access", p.email, name);
		return true;
	}

	@Override
	public synchronized void approve(String email, String accountId) {
		FileContents c = current();
		FileAccount target = null;
		for (FileAccount a : c.accounts) if (a.id.equals(accountId)) target = a;
		if (target == null) throw new IllegalArgumentException("no account " + accountId);
		String e = norm(email);
		c.pending.removeIf(p -> norm(p.email).equals(e));
		c.rejected.removeIf(x -> norm(x).equals(e));
		for (FileAccount a : c.accounts) a.members.removeIf(m -> norm(m).equals(e));    // one household each
		target.members.add(e);
		save();
		Log.info("{} approved into account {}", e, accountId);
	}

	@Override
	public synchronized void reject(String email) {
		FileContents c = current();
		String e = norm(email);
		c.pending.removeIf(p -> norm(p.email).equals(e));
		if (!hasEmail(c.rejected, e)) c.rejected.add(e);
		save();
		Log.info("{} rejected", e);
	}

	@Override
	public String summary() {
		return summary(current());
	}

	private static String summary(FileContents c) {
		return c.accounts.size() + " account(s), " + c.devices.size() + " device(s), " + c.pending.size() + " waiting";
	}

	private static Account toAccount(FileAccount a) {
		return new Account(a.id, listOf(a.members), listOf(a.cameras));
	}

	private static List<String> listOf(List<String> l) {
		return l == null ? List.of() : List.copyOf(l);
	}
}
