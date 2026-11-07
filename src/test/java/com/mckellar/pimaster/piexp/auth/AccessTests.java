package com.mckellar.pimaster.piexp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.mckellar.pimaster.piexp.auth.Access.Grant;
import com.mckellar.pimaster.piexp.auth.Access.Level;
import com.mckellar.pimaster.piexp.auth.Access.Rule;

// The four levels on their own: world, home, camera, event type.
class AccessTests {

	// two homes: ours (pet door, porch) and grandma's (her porch); anything else is in "home"
	static final Function<String, String> HOME_OF = c -> switch (c) {
		case "pet_door", "porch_cam" -> "ours";
		case "grandma_porch" -> "grandma";
		default -> "home";
	};

	static final Map<String, Object> CAT = Map.of("cat", true, "person", false);
	static final Map<String, Object> CAT_UNCHECKED = Map.of("cat", true);
	static final Map<String, Object> PERSON = Map.of("person", true);
	static final Map<String, Object> CAT_LEGS = Map.of("cat", true, "person", false, "legs", true);
	static final Map<String, Object> DOG = Map.of("dog", true, "person", false);

	static Access of(Grant... g) {
		return new Access(List.of(g), HOME_OF);
	}

	@Test
	void worldIsEveryCameraEvenNewOnes() {
		Access a = of(new Grant(Level.WORLD, null, Rule.EVERYTHING));
		for (String c : new String[] {"pet_door", "grandma_porch", "a_camera_added_next_year"}) {
			assertTrue(a.canSee(c));
			assertTrue(a.mayView(c, PERSON));
			assertFalse(a.needsLabels(c));
		}
	}

	@Test
	void homeIsItsCamerasOnly() {
		Access a = of(new Grant(Level.HOME, "ours", Rule.EVERYTHING));
		assertTrue(a.canSee("pet_door"));
		assertTrue(a.canSee("porch_cam"));
		assertFalse(a.canSee("grandma_porch"));
		assertFalse(a.mayView("grandma_porch", CAT));
		assertFalse(a.canSee(null));
	}

	@Test
	void cameraIsJustThatOne() {
		Access a = of(new Grant(Level.CAMERA, "pet_door", Rule.EVERYTHING));
		assertTrue(a.canSee("pet_door"));
		assertFalse(a.canSee("porch_cam"));
	}

	@Test
	void eventTypeOnAnyLevel() {
		Access a = of(new Grant(Level.WORLD, null, Rule.ANIMALS_ONLY));
		assertTrue(a.canSee("grandma_porch"));
		assertTrue(a.needsLabels("grandma_porch"));
		assertTrue(a.mayView("grandma_porch", CAT));
		assertFalse(a.mayView("grandma_porch", PERSON));
		assertFalse(a.mayView("grandma_porch", CAT_LEGS), "legs are a person");
		assertFalse(a.mayView("grandma_porch", CAT_UNCHECKED), "person must be explicitly false");
		assertFalse(a.mayView("grandma_porch", null), "never classified");
		Access cats = of(new Grant(Level.CAMERA, "pet_door", new Rule(Set.of("cat"), Set.of())));
		assertTrue(cats.mayView("pet_door", CAT_UNCHECKED), "no people rule, so person needn't be checked");
		assertFalse(cats.mayView("pet_door", DOG));
	}

	@Test
	void grantsAddUp() {
		// the whole porch, animals only on the pet door
		Access a = of(new Grant(Level.CAMERA, "porch_cam", Rule.EVERYTHING), new Grant(Level.CAMERA, "pet_door", Rule.ANIMALS_ONLY));
		assertTrue(a.mayView("porch_cam", PERSON));
		assertFalse(a.needsLabels("porch_cam"));
		assertTrue(a.needsLabels("pet_door"));
		assertFalse(a.mayView("pet_door", PERSON));
		assertTrue(a.mayView("pet_door", CAT));
		assertFalse(a.hidesPeople(), "people are visible on the porch");
		assertNull(a.shownLabels(), "anything on the porch");
		// a limited world grant plus a full home grant: full at home, limited elsewhere
		Access b = of(new Grant(Level.WORLD, null, Rule.ANIMALS_ONLY), new Grant(Level.HOME, "ours", Rule.EVERYTHING));
		assertTrue(b.mayView("pet_door", PERSON));
		assertFalse(b.mayView("grandma_porch", PERSON));
		assertTrue(b.mayView("grandma_porch", CAT));
	}

	@Test
	void everythingAndNothing() {
		assertTrue(Access.EVERYTHING.mayView("x", null));
		assertFalse(Access.NOTHING.canSee("x"));
		assertFalse(Access.NOTHING.mayView("x", CAT));
		assertTrue(Access.NOTHING.nothing());
		assertTrue(Access.cameras(List.of("pet_door")).canSee("pet_door"));
		assertFalse(Access.cameras(List.of("pet_door")).canSee("porch_cam"));
	}

	@Test
	void forTheWebAppAndTheAccessPage() {
		Access friends = of(new Grant(Level.CAMERA, "pet_door", Rule.ANIMALS_ONLY));
		assertTrue(friends.hidesPeople());
		assertEquals(Set.of("cat", "dog", "raccoon"), friends.shownLabels());
		assertEquals("Pet door: cats, dogs, raccoons; no people", friends.describe(id -> id));
		assertEquals("Home Grandma's house: all clips",
				of(new Grant(Level.HOME, "grandma", Rule.EVERYTHING)).describe(id -> "Grandma's house"));
		assertEquals("Every camera: cats", of(new Grant(Level.WORLD, null, new Rule(Set.of("cat"), null))).describe(id -> id));
	}
}
