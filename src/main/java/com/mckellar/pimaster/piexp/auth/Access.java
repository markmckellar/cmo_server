package com.mckellar.pimaster.piexp.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What a caller may see, as grants on four levels, broadest first:
 * <ol>
 * <li><b>world</b>: every camera in every home, including ones added later
 * <li><b>home</b>: every camera in one home (see {@link AccessStore#homeOf}), including ones added to it later
 * <li><b>camera</b>: one camera
 * <li><b>event type</b>: on any of the above, a {@link Rule} saying which clips (e.g. only cats, dogs and raccoons,
 * never one with a person in it)
 * </ol>
 * Grants add up: a clip may be seen if any grant covers its camera and that grant's rule lets it through. Every
 * decision about a camera or a clip goes through {@link #canSee} and {@link #mayView}.
 */
public final class Access {

	public enum Level { WORLD, HOME, CAMERA }

	/** Labels that mean a person (or part of one) is in a clip. */
	public static final Set<String> PEOPLE = Set.of("person", "legs", "delivery");

	/**
	 * Which clips a grant lets through. {@code show}: at least one of these labels must be true (null: any clip);
	 * {@code hide}: none of these may be true. A rule hiding "person" also needs "person" to be explicitly false,
	 * so a clip the AI never classified, or one it couldn't read, stays hidden.
	 */
	public record Rule(Set<String> show, Set<String> hide) {
		public static final Rule EVERYTHING = new Rule(null, Set.of());
		public static final Rule ANIMALS_ONLY = new Rule(Set.of("cat", "dog", "raccoon"), PEOPLE);

		public Rule {
			show = show == null ? null : lower(show);
			hide = hide == null ? Set.of() : lower(hide);
		}

		public boolean restricted() {
			return show != null || !hide.isEmpty();
		}

		public boolean allows(Map<String, Object> labels) {
			if (!restricted()) return true;
			if (labels == null) return false;
			Map<String, Object> l = new HashMap<>();
			for (Map.Entry<String, Object> e : labels.entrySet()) l.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
			for (String h : hide) if (Boolean.TRUE.equals(l.get(h))) return false;
			if (hide.contains("person") && !Boolean.FALSE.equals(l.get("person"))) return false;
			if (show == null) return true;
			for (String s : show) if (Boolean.TRUE.equals(l.get(s))) return true;
			return false;
		}

		/** In words, e.g. "cats, dogs, raccoons; no people", or "all clips". */
		public String describe() {
			if (!restricted()) return "all clips";
			List<String> parts = new ArrayList<>();
			if (show != null) parts.add(String.join(", ", show.stream().sorted().map(Access::plural).toList()));
			if (!hide.isEmpty()) parts.add(hide.containsAll(PEOPLE) || hide.contains("person") ? "no people"
					: "no " + String.join(", no ", hide.stream().sorted().map(Access::plural).toList()));
			return String.join("; ", parts);
		}

		private static Set<String> lower(Set<String> s) {
			Set<String> out = new LinkedHashSet<>();
			for (String x : s) if (x != null && !x.isBlank()) out.add(x.trim().toLowerCase(Locale.ROOT));
			return Set.copyOf(out);
		}
	}

	/** One grant: a level, which home or camera (null for world), and which clips. */
	public record Grant(Level level, String id, Rule rule) {
		public Grant {
			rule = rule == null ? Rule.EVERYTHING : rule;
		}

		boolean covers(String camera, Function<String, String> homeOf) {
			if (camera == null) return false;
			return switch (level) {
				case WORLD -> true;
				case HOME -> id != null && id.equals(homeOf.apply(camera));
				case CAMERA -> camera.equals(id);
			};
		}

		public String describe(Function<String, String> homeName) {
			String where = switch (level) {
				case WORLD -> "Every camera";
				case HOME -> "Home " + homeName.apply(id);
				case CAMERA -> cameraName(id);
			};
			return where + ": " + rule.describe();
		}
	}

	/** Everything (admins, a server at home in open mode). */
	public static final Access EVERYTHING = new Access(null, c -> null);
	/** Nothing (signed out, waiting for approval). */
	public static final Access NOTHING = new Access(List.of(), c -> null);

	private final List<Grant> grants;    // null: everything
	private final Function<String, String> homeOf;

	public Access(List<Grant> grants, Function<String, String> homeOf) {
		this.grants = grants == null ? null : List.copyOf(grants);
		this.homeOf = homeOf;
	}

	/** Only these cameras, every clip on them (e.g. a camera device uploading). */
	public static Access cameras(java.util.Collection<String> cameras) {
		return new Access(cameras.stream().map(c -> new Grant(Level.CAMERA, c, Rule.EVERYTHING)).toList(), c -> null);
	}

	public boolean everything() {
		return grants == null;
	}

	public boolean nothing() {
		return grants != null && grants.isEmpty();
	}

	public List<Grant> grants() {
		return grants == null ? List.of() : grants;
	}

	/** May the caller see this camera at all (some of its clips)? */
	public boolean canSee(String camera) {
		if (grants == null) return true;
		for (Grant g : grants) if (g.covers(camera, homeOf)) return true;
		return false;
	}

	/** Does deciding about this camera's clips need their labels (is every grant on it limited by type)? */
	public boolean needsLabels(String camera) {
		if (grants == null) return false;
		boolean covered = false;
		for (Grant g : grants) {
			if (!g.covers(camera, homeOf)) continue;
			if (!g.rule().restricted()) return false;
			covered = true;
		}
		return covered;
	}

	/** May the caller see this clip (on this camera, with these labels, or null if it has none)? */
	public boolean mayView(String camera, Map<String, Object> labels) {
		if (grants == null) return true;
		for (Grant g : grants) if (g.covers(camera, homeOf) && g.rule().allows(labels)) return true;
		return false;
	}

	/** For the web app: the labels this caller can ever find (null: any), to offer only those. */
	public Set<String> shownLabels() {
		if (grants == null) return null;
		Set<String> out = new LinkedHashSet<>();
		for (Grant g : grants) {
			if (g.rule().show() == null) return null;
			out.addAll(g.rule().show());
		}
		return out;
	}

	/**
	 * For the web app's filter buttons on one camera: the labels the caller can find there (null: any), from the
	 * grants covering it; empty if none covers it.
	 */
	public Set<String> shownLabels(String camera) {
		if (grants == null) return null;
		Set<String> out = new LinkedHashSet<>();
		for (Grant g : grants) {
			if (!g.covers(camera, homeOf)) continue;
			if (g.rule().show() == null) return null;
			out.addAll(g.rule().show());
		}
		return out;
	}

	/** The labels no clip on this camera may have for this caller (hidden by every grant covering it). */
	public Set<String> hiddenLabels(String camera) {
		if (grants == null) return Set.of();
		Set<String> out = null;
		for (Grant g : grants) {
			if (!g.covers(camera, homeOf)) continue;
			if (out == null) out = new LinkedHashSet<>(g.rule().hide());
			else out.retainAll(g.rule().hide());
		}
		return out == null ? Set.of() : out;
	}

	/** For the web app: is every clip this caller sees free of people (so "Hide people" means nothing)? */
	public boolean hidesPeople() {
		if (grants == null || grants.isEmpty()) return false;
		for (Grant g : grants) if (!g.rule().hide().contains("person")) return false;
		return true;
	}

	/** In words, one grant per part, e.g. "Pet door: cats, dogs, raccoons; no people". */
	public String describe(Function<String, String> homeName) {
		if (grants == null) return "Everything";
		if (grants.isEmpty()) return "Nothing";
		return String.join(" · ", grants.stream().map(g -> g.describe(homeName)).toList());
	}

	/** "pet_door" -> "Pet door" */
	public static String cameraName(String camera) {
		if (camera == null || camera.isEmpty()) return "";
		String n = camera.replace('_', ' ');
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	private static String plural(String label) {
		return switch (label) {
			case "cat" -> "cats";
			case "dog" -> "dogs";
			case "raccoon" -> "raccoons";
			case "person" -> "people";
			case "delivery" -> "deliveries";
			case "animal" -> "animals";
			default -> label;
		};
	}
}
