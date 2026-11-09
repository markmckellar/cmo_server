package com.mckellar.pimaster.piexp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which events have which labels, per camera, so a search for a rare label (raccoon) goes straight to the events
 * that have it instead of reading every event's me_ollama.json.
 * <p>
 * It is kept only in memory and built from the event folders, which stay the only record: nothing is written to
 * disk, so there is nothing to corrupt, and a restart builds it again. It is kept current when an event is uploaded,
 * relabeled or deleted ({@link #refresh}, {@link #remove}), and {@link #rebuild} re-checks it against the folders
 * every few minutes to catch anything changed behind the server's back. Each entry remembers the time and size of
 * the me_ollama.json it was read from, and {@link #labels} rereads a file that has changed, so callers re-check
 * every candidate before using it and a stale entry can't show a wrong event; at worst a just-changed event is
 * missing from a search until the next re-check. Until the first build finishes {@link #isReady} is false and
 * searches use the old full scan.
 */
public class LabelIndex {

	private static final Logger Log = LoggerFactory.getLogger(LabelIndex.class);

	/** One event: its date folder, the me_ollama.json it was read from (time and size, 0 if none) and its labels. */
	private record Entry(String date, long modified, long size, Map<String, Object> labels, Set<String> trueLabels) {
	}

	/** One camera's events, newest first, and for each label the events that have it. */
	private static class Camera {
		final TreeMap<String, Entry> events = new TreeMap<>(Comparator.reverseOrder());
		final Map<String, TreeSet<String>> byLabel = new HashMap<>();
	}

	private final File root;
	private final Function<File, Map<String, Object>> reader;
	private final Map<String, Camera> cameras = new HashMap<>();
	private volatile boolean ready;
	private volatile long builtAt;
	private volatile long buildMillis;

	/**
	 * @param root   the uploads folder (camera / date / event)
	 * @param reader an event folder's labels (its me_ollama.json without the backups), or null if it has none or
	 *               it can't be read
	 */
	public LabelIndex(File root, Function<File, Map<String, Object>> reader) {
		this.root = root;
		this.reader = reader;
	}

	public boolean isReady() {
		return ready;
	}

	/**
	 * An event's labels (unchangeable; null if it has none), from the index if its me_ollama.json is unchanged,
	 * else read again and the index updated. An event folder that is gone is dropped from the index.
	 */
	public Map<String, Object> labels(File eventDir) {
		File f = new File(eventDir, "me_ollama.json");
		long modified = f.lastModified(), size = f.length();    // 0 and 0 if the file is missing
		String camera = cameraOf(eventDir), name = eventDir.getName();
		synchronized (this) {
			Camera c = cameras.get(camera);
			Entry e = c == null ? null : c.events.get(name);
			if (e != null && e.modified() == modified && e.size() == size) return e.labels();
		}
		if (modified == 0 && !eventDir.isDirectory()) {
			remove(camera, name);
			return null;
		}
		Map<String, Object> labels = modified == 0 ? null : reader.apply(eventDir);
		labels = labels == null ? null : Collections.unmodifiableMap(new HashMap<>(labels));
		put(camera, name, new Entry(eventDir.getParentFile().getName(), modified, size, labels, trueLabels(labels)));
		return labels;
	}

	/** After an upload or a relabel: read the event's labels again (or drop it if its folder is gone). */
	public void refresh(File eventDir) {
		labels(eventDir);
	}

	/** After a delete. */
	public synchronized void remove(String camera, String name) {
		Camera c = cameras.get(camera);
		if (c == null) return;
		Entry old = c.events.remove(name);
		if (old != null) unlink(c, name, old);
	}

	/**
	 * The events of a camera that the index says match, newest first, as {date, event name}: with any of the
	 * labels (or all of them), between the dates (yyyyMMdd, inclusive, null for no limit) and older than "before"
	 * (an event name, or null). Callers re-check each one with {@link #labels}.
	 */
	public List<String[]> candidates(String camera, List<String> wanted, boolean all, String from, String to, String before) {
		return candidates(camera, wanted, all, from, to, before, Map.of());
	}

	/** The same, where a wanted label also matches others (e.g. person: person or legs). */
	public synchronized List<String[]> candidates(String camera, List<String> wanted, boolean all, String from, String to, String before,
			Map<String, List<String>> also) {
		List<String[]> out = new ArrayList<>();
		Camera c = cameras.get(camera);
		if (c == null || wanted.isEmpty()) return out;
		List<TreeSet<String>> sets = new ArrayList<>();
		for (String l : wanted) {
			TreeSet<String> s = new TreeSet<>(Comparator.reverseOrder());
			for (String a : also.getOrDefault(l, List.of(l))) s.addAll(c.byLabel.getOrDefault(a, new TreeSet<>()));
			sets.add(s);
		}
		TreeSet<String> names;
		if (all) {
			sets.sort(Comparator.comparingInt(TreeSet::size));    // walk the smallest, check it is in the others
			names = new TreeSet<>(Comparator.reverseOrder());
			for (String n : sets.get(0)) {
				boolean inAll = true;
				for (int i = 1; i < sets.size() && inAll; i++) inAll = sets.get(i).contains(n);
				if (inAll) names.add(n);
			}
		}
		else {
			names = new TreeSet<>(Comparator.reverseOrder());
			for (TreeSet<String> s : sets) names.addAll(s);
		}
		for (String n : names) {
			if (before != null && n.compareTo(before) >= 0) continue;
			String date = c.events.get(n).date();
			if (to != null && date.compareTo(to) > 0) continue;
			if (from != null && date.compareTo(from) < 0) break;    // newest first: the rest are older still
			out.add(new String[] {date, n});
		}
		return out;
	}

	/**
	 * Check every event folder against the index: add new events, reread changed labels, drop events that are
	 * gone. The first one makes the index ready. A failure leaves the index as it was (and searches still re-check
	 * what it says), so it is only logged; the next re-check tries again.
	 */
	public void rebuild() {
		long start = System.nanoTime();
		try {
			Map<String, Set<String>> seen = new HashMap<>();
			File[] cameraDirs = root.listFiles(File::isDirectory);
			if (cameraDirs == null) cameraDirs = new File[0];
			for (File cameraDir : cameraDirs) {
				Set<String> names = seen.computeIfAbsent(cameraDir.getName(), k -> new HashSet<>());
				File[] dateDirs = cameraDir.listFiles(File::isDirectory);
				if (dateDirs == null) continue;
				for (File dateDir : dateDirs) {
					File[] eventDirs = dateDir.listFiles(File::isDirectory);
					if (eventDirs == null) continue;
					for (File eventDir : eventDirs) {
						labels(eventDir);
						names.add(eventDir.getName());
					}
				}
			}
			synchronized (this) {
				for (Map.Entry<String, Camera> c : cameras.entrySet()) {
					Set<String> names = seen.getOrDefault(c.getKey(), Set.of());
					for (String n : new ArrayList<>(c.getValue().events.keySet()))
						if (!names.contains(n)) remove(c.getKey(), n);
				}
			}
			buildMillis = (System.nanoTime() - start) / 1000000;
			builtAt = System.currentTimeMillis();
			boolean first = !ready;
			ready = true;
			if (first) Log.info("label index ready: {}", describe());
		}
		catch (RuntimeException e) {
			Log.error("label index re-check failed; keeping what it had: " + e, e);
		}
	}

	/**
	 * For the admin page's preview: per camera, how many events there are and how many of them pass the test
	 * (camera, labels or null). Only what is in memory; nothing is read.
	 */
	public synchronized Map<String, int[]> count(java.util.function.BiPredicate<String, Map<String, Object>> visible) {
		Map<String, int[]> out = new TreeMap<>();
		for (Map.Entry<String, Camera> c : cameras.entrySet()) {
			int[] n = new int[2];
			for (Entry e : c.getValue().events.values()) {
				n[0]++;
				if (visible.test(c.getKey(), e.labels())) n[1]++;
			}
			out.put(c.getKey(), n);
		}
		return out;
	}

	/** For /catcam/health: is it ready, how many events, how long the last re-check took and when. */
	public synchronized Map<String, Object> describe() {
		int events = 0;
		for (Camera c : cameras.values()) events += c.events.size();
		Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("ready", ready);
		m.put("events", events);
		m.put("last_check_ms", buildMillis);
		m.put("last_check", builtAt == 0 ? null : java.time.Instant.ofEpochMilli(builtAt).toString());
		return m;
	}

	private synchronized void put(String camera, String name, Entry e) {
		Camera c = cameras.computeIfAbsent(camera, k -> new Camera());
		Entry old = c.events.put(name, e);
		if (old != null) unlink(c, name, old);
		for (String l : e.trueLabels()) c.byLabel.computeIfAbsent(l, k -> new TreeSet<>(Comparator.reverseOrder())).add(name);
	}

	private static void unlink(Camera c, String name, Entry old) {
		for (String l : old.trueLabels()) {
			TreeSet<String> s = c.byLabel.get(l);
			if (s != null) {
				s.remove(name);
				if (s.isEmpty()) c.byLabel.remove(l);
			}
		}
	}

	private static String cameraOf(File eventDir) {
		File date = eventDir.getParentFile();
		File camera = date == null ? null : date.getParentFile();
		return camera == null ? "" : camera.getName();
	}

	/** The labels set to true, in lower case. */
	private static Set<String> trueLabels(Map<String, Object> labels) {
		if (labels == null) return Set.of();
		Set<String> s = new HashSet<>();
		for (Map.Entry<String, Object> e : labels.entrySet())
			if (Boolean.TRUE.equals(e.getValue())) s.add(e.getKey().toLowerCase(Locale.ROOT));
		return Set.copyOf(s);
	}
}
