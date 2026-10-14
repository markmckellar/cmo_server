package com.mckellar.pimaster.piexp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This code's version, from its git tags (see release.bash), the same way in every cmo repo: 1.3.0 on a
// release, 1.3.0+4 four commits after it, 1.3.0+4.dirty (1.3.0+dirty on the release itself) with
// uncommitted changes too. The server runs from its git checkout (mvn spring-boot:run), so it asks git
// once, at startup. Without git it falls back to the pom's version, which release.bash keeps the same
// as the tag, else "unknown".
public final class CodeVersion {

	private static final Pattern DESCRIBED = Pattern.compile("^v([0-9.]+)-([0-9]+)-g[0-9a-f]+(-dirty)?$");

	private static String version;

	private CodeVersion() {
	}

	// e.g. "1.3.0"
	public static synchronized String version() {
		if(version == null) version = describe(new File(System.getProperty("user.dir")));
		return(version);
	}

	// e.g. "cmo_server:1.3.0", for log lines and JSON files
	public static String code() {
		return("cmo_server:"+version());
	}

	// What the build says when git can't: the pom's version (spring-boot-maven-plugin build-info)
	static synchronized void setFallback(String pomVersion) {
		if("unknown".equals(version()) && pomVersion != null && !pomVersion.isEmpty()) version = pomVersion;
	}

	static String describe(File dir) {
		try {
			Process git = new ProcessBuilder("git", "-C", dir.getPath(), "describe", "--tags", "--match", "v[0-9]*",
					"--long", "--dirty").redirectErrorStream(false).start();
			String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if(!git.waitFor(10, TimeUnit.SECONDS) || git.exitValue() != 0) return("unknown");
			return(format(out));
		}
		catch(Exception e) {
			return("unknown");
		}
	}

	// "v1.3.0-4-gabc1234-dirty" -> "1.3.0+4.dirty"
	static String format(String described) {
		Matcher m = DESCRIBED.matcher(described);
		if(!m.matches()) return("unknown");
		String v = m.group(1);
		boolean ahead = !"0".equals(m.group(2));
		if(ahead) v += "+"+m.group(2);
		if(m.group(3) != null) v += ahead ? ".dirty" : "+dirty";
		return(v);
	}
}
