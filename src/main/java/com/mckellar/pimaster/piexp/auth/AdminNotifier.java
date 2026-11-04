package com.mckellar.pimaster.piexp.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the admin on Telegram that someone asked for access. Reads TOKEN=... and CHAT_ID=... lines from a
 * config file (mode 600, never in git; the same format as ai-chat-send's). No file: the request is only logged.
 * Sends in the background, so a slow Telegram never holds up a sign-in.
 */
public class AdminNotifier {

	private static final Logger Log = LoggerFactory.getLogger(AdminNotifier.class);

	private final Path configFile;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public AdminNotifier(Path configFile) {
		this.configFile = configFile;
	}

	public void send(String text) {
		Map<String, String> conf = readConfig();
		String token = conf.get("TOKEN"), chat = conf.get("CHAT_ID");
		if (token == null || chat == null) {
			Log.info("no Telegram config ({}); not sent: {}", configFile, text);
			return;
		}
		String form = "chat_id=" + URLEncoder.encode(chat, StandardCharsets.UTF_8)
				+ "&disable_web_page_preview=true&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)).build();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
			if (e != null) Log.warn("Telegram alert failed: {}", e.toString());
			else if (r.statusCode() != 200) Log.warn("Telegram alert failed: HTTP {}", r.statusCode());
		});
	}

	private Map<String, String> readConfig() {
		Map<String, String> m = new HashMap<>();
		if (configFile == null || !Files.isReadable(configFile)) return m;
		try {
			for (String line : Files.readAllLines(configFile)) {
				String l = line.trim();
				int eq = l.indexOf('=');
				if (l.startsWith("#") || eq <= 0) continue;
				String v = l.substring(eq + 1).trim();
				if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0))
					v = v.substring(1, v.length() - 1);
				m.put(l.substring(0, eq).trim(), v);
			}
		}
		catch (Exception e) {
			Log.warn("Telegram config {}: {}", configFile, e.toString());
		}
		return m;
	}
}
