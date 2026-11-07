package com.mckellar.pimaster.piexp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mckellar.pimaster.piexp.auth.AccessStore;
import com.mckellar.pimaster.piexp.auth.AuthConfig;
import com.mckellar.pimaster.piexp.auth.Caller;
import com.mckellar.pimaster.piexp.fileioservice.FileStorageProperties;
import com.mckellar.pimaster.piexp.fileioservice.FileStorageService;
import com.mckellar.pimaster.piexp.fileioservice.UploadFileResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@EnableWebSecurity
@SpringBootApplication
@RestController
@EnableConfigurationProperties({
    FileStorageProperties.class
})
public class PimasterApplication extends WebSecurityConfigurerAdapter {


    private static final Logger Log = LoggerFactory.getLogger(PimasterApplication.class);



	public static void main(String[] args) {
		SpringApplication.run(PimasterApplication.class, args);
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
	    // the defaults allow GET, HEAD and POST; the web client also deletes events and changes their classification
	    http.cors().configurationSource(request -> {
	    	CorsConfiguration cors = new CorsConfiguration().applyPermitDefaultValues();
	    	cors.addAllowedMethod(HttpMethod.DELETE);
	    	cors.addAllowedMethod(HttpMethod.PUT);
	    	return(cors);
	    });
	    http.csrf().disable();
	}

    /**
     * The site's front door and the web app's folder open the web app, so a link doesn't need {@code index.html}.
     * The Location is a path, not a full URL, so it stays https behind Caddy (see AuthFilter).
     */
    @GetMapping({"/", "/cmo_client", "/cmo_client/"})
    ResponseEntity<Void> home(HttpServletRequest request) {
    	String query = request.getQueryString();
    	return ResponseEntity.status(HttpStatus.FOUND)
    			.header(HttpHeaders.LOCATION, request.getContextPath() + "/cmo_client/index.html" + (query == null ? "" : "?" + query))
    			.build();
    }

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AuthConfig authConfig;

    @Autowired
    private AccessStore accessStore;


    // set by the build (spring-boot-maven-plugin build-info); missing when run from an IDE
    @Autowired(required = false)
    private BuildProperties buildProperties;

    // which code this is, in the log once at startup
    @PostConstruct
    void logCodeVersion() {
    	if(buildProperties != null) CodeVersion.setFallback(buildProperties.getVersion());
    	Log.info("running "+CodeVersion.code());
    }

    // This server's version, e.g. {"code": "cmo_server:1.3.0", "version": "1.3.0", "build_time": "..."}
    // (see CodeVersion and release.bash)
    @GetMapping("/catcam/version")
    public Map<String,Object> GetVersion() {
    	Map<String,Object> result = new LinkedHashMap<String,Object>();
    	result.put("code", CodeVersion.code());
    	result.put("version", CodeVersion.version());
    	result.put("build_time", buildProperties == null || buildProperties.getTime() == null ? null : buildProperties.getTime().toString());
    	return(result);
    }

    // A camera, and the home it's in (for a home picker in the web app, later)
    class MeGroup {
      	public String meGroup;
      	public String home;
      	public String homeName;

    	public MeGroup(String meGroup, String home, String homeName) {
    		this.meGroup = meGroup;
    		this.home = home;
    		this.homeName = homeName;
    	}
    }

    class MeEventGroup {
      	public String meEventGroup;

    	public MeEventGroup(String meEventGroup) {
    		this.meEventGroup = meEventGroup;
    	}
    }


    // Each me_data.json is ~80KB, mostly me_delta_array, and the event lists build the
    // whole response in memory. A big howMayRecords, or a pile of requests that clients
    // have already given up on, can run the server out of heap. So cap the records per
    // request and only let a few of these requests run at once.
    private static final int MAX_RECORDS = 200;
    private static final Semaphore eventListPermits = new Semaphore(4);

    private int capRecords(int howMayRecords) {
    	if(howMayRecords > MAX_RECORDS) {
    		Log.warn("howMayRecords="+howMayRecords+" capped to "+MAX_RECORDS);
    		return(MAX_RECORDS);
    	}
    	return(howMayRecords);
    }

    private void acquireEventListPermit() {
    	try {
    		if(eventListPermits.tryAcquire(10, TimeUnit.SECONDS)) return;
    	}
    	catch(InterruptedException e) {
    		Thread.currentThread().interrupt();
    	}
    	Log.warn("too many event list requests at once, returning 503");
    	throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "server busy, try again");
    }

    // brief=true: replace me_delta_array with me_delta_count, me_first_event_time and
    // me_last_event_time, which is all the web client uses it for.
    private MeEvent briefMeEvent(MeEvent meEvent) {
    	Object deltas = meEvent.remove("me_delta_array");
    	if(deltas instanceof List) {
    		List<?> deltaList = (List<?>)deltas;
    		meEvent.put("me_delta_count", deltaList.size());
    		if(!deltaList.isEmpty()) {
    			meEvent.put("me_first_event_time", eventTimeIso(deltaList.get(0)));
    			meEvent.put("me_last_event_time", eventTimeIso(deltaList.get(deltaList.size()-1)));
    		}
    	}
    	return(meEvent);
    }

    private Object eventTimeIso(Object delta) {
    	return((delta instanceof Map) ? ((Map<?,?>)delta).get("event_time_iso") : null);
    }

    private final Gson gson = new Gson();

    private Path uploadRoot() {
    	return(new File(fileStorageService.getFileStorageProperties().getUploadDir()).toPath().toAbsolutePath().normalize());
    }

    // A folder under the uploads folder, from names in the URL. Refuses anything that would
    // leave the uploads folder.
    private File uploadDir(String... names) {
    	Path root = uploadRoot();
    	Path dir = root;
    	for(String name : names) dir = dir.resolve(name);
    	dir = dir.normalize();
    	if(!dir.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad path");
    	return(dir.toFile());
    }

    // The folders in dir, by name, newest (largest) first. Empty if dir doesn't exist, so an
    // unknown camera or date gives an empty list rather than an error.
    private static List<File> subDirsNewestFirst(File dir) {
    	File[] dirs = dir.listFiles(File::isDirectory);
    	if(dirs == null) return(new ArrayList<File>());
    	Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
    	return(new ArrayList<File>(Arrays.asList(dirs)));
    }

    // An event's me_data.json, or null if it is missing or can't be read.
    private MeEvent readMeData(File eventDir) {
    	File jsonFile = new File(eventDir, "me_data.json");
    	if(!jsonFile.isFile()) return(null);
    	try {
    		String json = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
    		return(gson.fromJson(json, MeEvent.class));
    	}
    	catch(Exception e) {
    		Log.warn("error reading in json file="+jsonFile.getPath()+" error="+e.getMessage());
    		return(null);
    	}
    }

    // An event's me_ollama.json, or null if it is missing or can't be read.
    private Map<String,Object> readOllama(File eventDir) {
    	File ollamaFile = new File(eventDir, "me_ollama.json");
    	if(!ollamaFile.isFile()) return(null);
    	try {
    		String json = Files.readString(ollamaFile.toPath(), StandardCharsets.UTF_8);
    		return(gson.fromJson(json, new TypeToken<Map<String,Object>>() {}.getType()));
    	}
    	catch(Exception e) {
    		Log.warn("error reading in json file="+ollamaFile.getPath()+" error="+e.getMessage());
    		return(null);
    	}
    }

    // May this caller see this event (camera / date / event)? Its labels are read only when the caller's grants
    // on that camera are limited by event type.
    private boolean mayView(Caller caller, File eventDir) {
    	File date = eventDir.getParentFile();
    	String camera = date == null || date.getParentFile() == null ? null : date.getParentFile().getName();
    	if(!caller.canSee(camera)) return(false);
    	return(!caller.needsLabels(camera) || caller.mayView(camera, cachedLabels(eventDir)));
    }

    // Which events have which labels, so a search for a rare label (raccoon) needn't read every event; see
    // LabelIndex. Only in memory, built from the event folders at startup and re-checked every few minutes.
    private volatile LabelIndex labelIndex;
    private static final long LABEL_INDEX_RECHECK_MINUTES = 5;

    private LabelIndex labelIndex() {
    	LabelIndex i = labelIndex;
    	if(i == null) synchronized(this) {
    		if(labelIndex == null) labelIndex = new LabelIndex(uploadRoot().toFile(), dir -> ollamaLabels(readOllama(dir)));
    		i = labelIndex;
    	}
    	return(i);
    }

    // An event's labels (me_ollama.json without its backups), or null if it has none. Don't change the map.
    private Map<String,Object> cachedLabels(File eventDir) {
    	return(labelIndex().labels(eventDir));
    }

    // Build the label index in the background at startup, then re-check it against the folders every few minutes
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startLabelIndex() {
    	java.util.concurrent.ScheduledExecutorService ex = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
    		Thread t = new Thread(r, "label-index");
    		t.setDaemon(true);
    		t.setPriority(Thread.MIN_PRIORITY);
    		return(t);
    	});
    	ex.scheduleWithFixedDelay(() -> labelIndex().rebuild(), 0, LABEL_INDEX_RECHECK_MINUTES, TimeUnit.MINUTES);
    }

    private static boolean longEnough(MeEvent meEvent, Double minimumDuration) {
    	return(meEvent != null && meEvent.hasMeTime() && meEvent.getMeTime() > minimumDuration);
    }

	@RequestMapping("/catcam/meeventgroup/{meGroup}/{meName}")
    public List<MeEvent> GetMeEvent(
    		@PathVariable String meGroup,
    		@PathVariable String meName,
    		@RequestParam int howMayRecords,
    		@RequestParam int startRecord,
    		@RequestParam Double minimumDuration,
    		@RequestParam(defaultValue = "false") boolean brief,
    		HttpServletRequest request) throws IOException  {
        Log.info("/catcam/meeventgroup");
        howMayRecords = capRecords(howMayRecords);
        acquireEventListPermit();
        try {
        	return(readMeEvents(meGroup, meName, howMayRecords, startRecord, minimumDuration, brief, Caller.of(request)));
        }
        finally {
        	eventListPermits.release();
        }
    }

    // The events of one date, newest first. startRecord counts event folders, read or not.
    private List<MeEvent> readMeEvents(String meGroup, String meName, int howMayRecords,
    		int startRecord, Double minimumDuration, boolean brief, Caller caller) {
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	List<File> eventDirs = subDirsNewestFirst(uploadDir(meGroup, meName));

        long startTime = System.nanoTime();
        for (int i=startRecord;i<eventDirs.size();i++)
        {
        	// we got all we need :)
        	if(list.size()>=howMayRecords) break;
        	if(!mayView(caller, eventDirs.get(i))) continue;
        	MeEvent meEvent = readMeData(eventDirs.get(i));
        	if(longEnough(meEvent, minimumDuration))
        		list.add(brief ? briefMeEvent(meEvent) : meEvent);
        }
        long elapsedTime = (System.nanoTime() - startTime)/1000000;
        Log.info("GetMeEventGroups: " + meGroup+"/"+meName+":list="+list.size()+" elapsedTime="+elapsedTime);

        return(list);
    }

    // Search events, newest first, with filters that stack like a search site's:
    //   labels   classifications (comma separated; "label" is the old one-label form), e.g. raccoon,cat
    //   match    "any" (default: at least one of the labels) or "all" (every label in the same event)
    //   without  classifications the event must not have, e.g. person
    //   from, to dates (yyyyMMdd, inclusive); either may be left out
    // No filters at all lists the newest events. For the next page pass the me_name of the last event
    // received as "before". Each event comes with its me_ollama.json as "me_ollama".
	@RequestMapping({"/catcam/search", "/catcam/search/{meGroup}"})
    public List<MeEvent> SearchMeEvents(
    		@PathVariable(required = false) String meGroup,
    		@RequestParam(required = false) String label,
    		@RequestParam(required = false) String labels,
    		@RequestParam(defaultValue = "any") String match,
    		@RequestParam(required = false) String without,
    		@RequestParam(required = false) String from,
    		@RequestParam(required = false) String to,
    		@RequestParam(required = false) String cameras,
    		@RequestParam int howMayRecords,
    		@RequestParam Double minimumDuration,
    		@RequestParam(required = false) String before,
    		@RequestParam(defaultValue = "false") boolean brief,
    		HttpServletRequest request) throws IOException  {
    	List<String> wanted = labelList(label, labels);
    	List<String> unwanted = labelList(null, without);
    	boolean all = "all".equalsIgnoreCase(match);
    	from = dateParam("from", from);
    	to = dateParam("to", to);
        Log.info("/catcam/search labels="+wanted+" match="+(all ? "all" : "any")+" without="+unwanted+" from="+from+" to="+to+" before="+before);
        howMayRecords = capRecords(howMayRecords);
        acquireEventListPermit();
        try {
        	Caller caller = Caller.of(request);
        	if(meGroup != null)
        		return(searchMeEvents(meGroup, wanted, all, unwanted, from, to, howMayRecords, minimumDuration, before, brief, caller));
        	// no camera: every camera the caller may see (or those of them in "cameras", comma separated), merged newest first. Each camera gives up to a page, so the
        	// newest page overall is among them; event names start with date and time, so they sort across cameras.
        	List<String> only = labelList(null, cameras);
        	List<MeEvent> merged = new ArrayList<MeEvent>();
        	for (File g : subDirsNewestFirst(uploadRoot().toFile()))
        		if(caller.canSee(g.getName()) && !g.getName().contains(".") && (only.isEmpty() || only.contains(g.getName().toLowerCase(Locale.ROOT))))
        			merged.addAll(searchMeEvents(g.getName(), wanted, all, unwanted, from, to, howMayRecords, minimumDuration, before, brief, caller));
        	merged.sort(Comparator.comparing((MeEvent e) -> String.valueOf(e.get("me_name"))).reversed());
        	return(merged.size() > howMayRecords ? new ArrayList<MeEvent>(merged.subList(0, howMayRecords)) : merged);
        }
        finally {
        	eventListPermits.release();
        }
    }

    // Labels from "a,b" (and the old single label), lower case, without blanks or repeats
    private static List<String> labelList(String one, String many) {
    	List<String> list = new ArrayList<String>();
    	for (String part : ((one == null ? "" : one) + "," + (many == null ? "" : many)).split(",")) {
    		String l = part.trim().toLowerCase(Locale.ROOT);
    		if(!l.isEmpty() && !list.contains(l)) list.add(l);
    	}
    	return(list);
    }

    // A date filter: yyyyMMdd (dashes allowed, e.g. 2026-11-06), or null if not given
    private static String dateParam(String name, String value) {
    	if(value == null || value.isBlank()) return(null);
    	String d = value.trim().replace("-", "");
    	if(!d.matches("\\d{8}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be a date like 20261106");
    	return(d);
    }

    // Search labels that also match others the AI writes: people see "person" and "legs" (a person's legs or
    // feet) as one thing, so a search for person finds legs too, and "without person" leaves both out. The
    // files keep both labels as the AI wrote them.
    static final Map<String,List<String>> SEARCH_ALSO = Map.of("person", List.of("person", "legs"));

    static boolean hasSearchLabel(Map<String,Object> ollama, String label) {
    	for (String l : SEARCH_ALSO.getOrDefault(label, List.of(label)))
    		if(hasOllamaLabel(ollama, l)) return(true);
    	return(false);
    }

    // Does an event with these labels pass the label filters?
    static boolean matchesLabels(Map<String,Object> ollama, List<String> wanted, boolean all, List<String> unwanted) {
    	for (String l : unwanted) if(hasSearchLabel(ollama, l)) return(false);
    	if(wanted.isEmpty()) return(true);
    	for (String l : wanted) {
    		boolean has = hasSearchLabel(ollama, l);
    		if(all && !has) return(false);
    		if(!all && has) return(true);
    	}
    	return(all);
    }

    private List<MeEvent> searchMeEvents(String meGroup, List<String> wanted, boolean all, List<String> unwanted,
    		String from, String to, int howMayRecords, Double minimumDuration, String before, boolean brief, Caller caller) {
    	// with labels to look for, ask the index which events have them; if it isn't ready yet, or anything goes
    	// wrong with it, read every event instead (slower, same answer)
    	if(!wanted.isEmpty() && labelIndex().isReady()) {
    		try {
    			return(searchByIndex(meGroup, wanted, all, unwanted, from, to, howMayRecords, minimumDuration, before, brief, caller));
    		}
    		catch(ResponseStatusException e) {
    			throw e;
    		}
    		catch(RuntimeException e) {
    			Log.error("label index search failed, reading every event instead: "+e, e);
    		}
    	}
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	boolean byLabel = !wanted.isEmpty() || !unwanted.isEmpty() || caller.needsLabels(meGroup);

    	// event names start with their date (yyyyMMdd_...) so whole dates after "before" can be skipped
    	String beforeDate = (before != null && before.length() >= 8) ? before.substring(0,8) : null;
    	int scanned = 0;
        long startTime = System.nanoTime();

        for (File dateDir : subDirsNewestFirst(uploadDir(meGroup)))
        {
        	if(list.size()>=howMayRecords) break;
        	String date = dateDir.getName();
        	if(beforeDate != null && date.compareTo(beforeDate) > 0) continue;
        	if(to != null && date.compareTo(to) > 0) continue;
        	if(from != null && date.compareTo(from) < 0) break;    // newest first: every later folder is older still

        	for (File eventDir : subDirsNewestFirst(dateDir))
        	{
        		if(list.size()>=howMayRecords) break;
        		if(before != null && eventDir.getName().compareTo(before) >= 0) continue;

        		scanned++;
        		if(byLabel) {
        			Map<String,Object> ollama = cachedLabels(eventDir);
        			if(!caller.mayView(meGroup, ollama)) continue;
        			if(!matchesLabels(ollama, wanted, all, unwanted)) continue;
        		}

        		MeEvent meEvent = readMeData(eventDir);
        		if(longEnough(meEvent, minimumDuration)) {
        			if(brief) briefMeEvent(meEvent);
        			meEvent.put("me_ollama", readOllama(eventDir));    // so the web app needn't ask for each event's labels
        			list.add(meEvent);
        		}
        	}
        }

        long elapsedTime = (System.nanoTime() - startTime)/1000000;
        Log.info("SearchMeEvents: list="+list.size()+" scanned="+scanned+" elapsedTime="+elapsedTime);

        return(list);
    }

    // A label search through the index: only the events it says have the labels are read, and each one is
    // checked against its own files first (labels() rereads a me_ollama.json that changed), so a stale index
    // entry can't put a wrong event in the results
    private List<MeEvent> searchByIndex(String meGroup, List<String> wanted, boolean all, List<String> unwanted,
    		String from, String to, int howMayRecords, Double minimumDuration, String before, boolean brief, Caller caller) {
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	uploadDir(meGroup);    // refuses a camera name that would leave the uploads folder
    	long startTime = System.nanoTime();
    	int checked = 0;
    	for (String[] c : labelIndex().candidates(meGroup, wanted, all, from, to, before, SEARCH_ALSO)) {
    		if(list.size()>=howMayRecords) break;
    		File eventDir = uploadDir(meGroup, c[0], c[1]);
    		checked++;
    		Map<String,Object> labels = cachedLabels(eventDir);
    		if(!eventDir.isDirectory() || !caller.mayView(meGroup, labels) || !matchesLabels(labels, wanted, all, unwanted)) continue;
    		MeEvent meEvent = readMeData(eventDir);
    		if(longEnough(meEvent, minimumDuration)) {
    			if(brief) briefMeEvent(meEvent);
    			meEvent.put("me_ollama", readOllama(eventDir));
    			list.add(meEvent);
    		}
    	}
    	Log.info("SearchMeEvents (index): list="+list.size()+" checked="+checked+" elapsedTime="+(System.nanoTime() - startTime)/1000000);
    	return(list);
    }

    // true if the event's me_ollama.json has the label (any case) set to true
    private static boolean hasOllamaLabel(Map<String,Object> ollama,String label) {
    	if(ollama == null) return(false);
    	for (Map.Entry<String,Object> entry : ollama.entrySet())
    		if(entry.getKey().equalsIgnoreCase(label) && Boolean.TRUE.equals(entry.getValue()))
    			return(true);
    	return(false);
    }

	@RequestMapping("/catcam/megroups")
    public List<MeGroup> GetMeGroups(HttpServletRequest request) throws IOException  {
        Log.info("/catcam/megroups");

    	Caller caller = Caller.of(request);
    	List<MeGroup> list = new ArrayList<MeGroup>();
    	for (File f : subDirsNewestFirst(uploadRoot().toFile()))
    		if(caller.canSee(f.getName()))    // only the caller's cameras
    			list.add(new MeGroup(f.getName(), accessStore.homeOf(f.getName()), accessStore.homeName(accessStore.homeOf(f.getName()))));
    	Collections.reverse(list);    // A to Z

        Log.info("GetMeGroups: list="+list.size());
        return(list);
    }

	@RequestMapping("/catcam/meeventgroup/{meGroup}")
    public List<MeEventGroup> GetMeEventGroups(@PathVariable String meGroup,@RequestParam int howMayRecords,
    		HttpServletRequest request) throws IOException  {
        Log.info("/catcam/meeventgroup");

    	List<MeEventGroup> list = new ArrayList<MeEventGroup>();
    	for (File f : subDirsNewestFirst(uploadDir(meGroup)))
    	{
          	// we got all we need :)
        	if(list.size()>=howMayRecords) break;
        	list.add(new MeEventGroup(f.getName()));
        }

        Log.info("GetMeEventGroups: " + meGroup+":list="+list.size());
        return(list);
    }

    // Everything about one event in one small response: its me_data.json (brief), me_ollama.json,
    // the files it has, and whether it is complete (me_data.json and the movie and picture it names
    // are all there). For checking that an upload fully landed.
    @GetMapping("/catcam/event/{meGroup}/{meEventGroup}/{meName}")
    public Map<String,Object> GetEvent(
    		@PathVariable String meGroup,
    		@PathVariable String meEventGroup,
    		@PathVariable String meName,
    		HttpServletRequest request) throws IOException {
    	Log.info("/catcam/event/"+meGroup+"/"+meEventGroup+"/"+meName);
    	File eventDir = uploadDir(meGroup, meEventGroup, meName);
    	if(!eventDir.isDirectory() || !mayView(Caller.of(request), eventDir))
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such event");

    	Map<String,Long> files = new LinkedHashMap<String,Long>();
    	Path base = eventDir.toPath();
    	try (Stream<Path> walk = Files.walk(base)) {
    		for (Path p : walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList()))
    			files.put(base.relativize(p).toString().replace(File.separatorChar, '/'), p.toFile().length());
    	}

    	MeEvent meData = readMeData(eventDir);
    	boolean complete = meData != null;
    	if(meData != null)
    		for (String key : new String[] {"me_video_name", "me_rep_image"})
    			if(meData.get(key) != null && !files.containsKey(meData.get(key).toString()))
    				complete = false;

    	Map<String,Object> result = new LinkedHashMap<String,Object>();
    	result.put("me_group", meGroup);
    	result.put("me_event_group", meEventGroup);
    	result.put("me_name", meName);
    	result.put("complete", complete);
    	result.put("files", files);
    	result.put("me_ollama", readOllama(eventDir));
    	result.put("me_data", meData == null ? null : briefMeEvent(meData));
    	return(result);
    }

    // Delete one event: its folder and everything in it. The date folder goes too if that
    // leaves it empty. 404 if there is no such event.
    @DeleteMapping("/catcam/event/{meGroup}/{meEventGroup}/{meName}")
    public Map<String,Object> DeleteEvent(
    		@PathVariable String meGroup,
    		@PathVariable String meEventGroup,
    		@PathVariable String meName) throws IOException {
    	Log.info("DELETE /catcam/event/"+meGroup+"/"+meEventGroup+"/"+meName);
    	File eventDir = uploadDir(meGroup, meEventGroup, meName);
    	// only ever an event folder, three levels down; never a date, a camera or the uploads folder
    	File dateDir = eventDir.getParentFile();
    	if(dateDir == null || dateDir.getParentFile() == null
    			|| !uploadRoot().equals(dateDir.getParentFile().getParentFile().toPath()))
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad path");
    	if(!eventDir.isDirectory()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such event");

    	int deleted = 0;
    	try (Stream<Path> walk = Files.walk(eventDir.toPath())) {
    		// deepest first, so each folder is empty by the time it is deleted
    		for (Path p : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
    			Files.delete(p);
    			deleted++;
    		}
    	}
    	String[] left = dateDir.list();
    	if(left != null && left.length == 0) Files.deleteIfExists(dateDir.toPath());
    	Log.info("DeleteEvent: "+meGroup+"/"+meEventGroup+"/"+meName+" deleted="+deleted);
    	labelIndex().remove(meGroup, meName);

    	Map<String,Object> result = new LinkedHashMap<String,Object>();
    	result.put("me_group", meGroup);
    	result.put("me_event_group", meEventGroup);
    	result.put("me_name", meName);
    	result.put("deleted", true);
    	return(result);
    }

    // me_ollama.json keys that aren't labels: copies of the classification from before someone
    // changed it, when they last did, and the server code that wrote it (e.g. cmo_server:1.3.0)
    private static final String OLLAMA_ORIGINAL = "me_ollama_original";
    private static final String OLLAMA_PREVIOUS = "me_ollama_previous";
    private static final String OLLAMA_CHANGED = "me_ollama_changed";
    private static final String OLLAMA_CHANGED_BY = "me_ollama_changed_by";

    // for writing me_ollama.json: readable, and keeps a null original (an event ollama never classified)
    private final Gson ollamaWriter = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    // Just the labels of a me_ollama.json, without the backups (null for null)
    private static Map<String,Object> ollamaLabels(Map<String,Object> ollama) {
    	if(ollama == null) return(null);
    	Map<String,Object> labels = new LinkedHashMap<String,Object>();
    	for (Map.Entry<String,Object> entry : ollama.entrySet())
    		if(!entry.getKey().startsWith("me_")) labels.put(entry.getKey(), entry.getValue());
    	return(labels);
    }

    // Change an event's classification to the labels in the body, e.g. {"cat": true, "delivery": false}.
    // At least one label must be true. Nothing is lost: the first change keeps what ollama said as
    // me_ollama_original, and each later change keeps the classification it replaces as me_ollama_previous.
    // Returns the new me_ollama.json. 404 if there is no such event.
    @PutMapping("/catcam/event/{meGroup}/{meEventGroup}/{meName}/classification")
    public synchronized Map<String,Object> PutClassification(
    		@PathVariable String meGroup,
    		@PathVariable String meEventGroup,
    		@PathVariable String meName,
    		@RequestBody Map<String,Object> labels) throws IOException {
    	Log.info("PUT /catcam/event/"+meGroup+"/"+meEventGroup+"/"+meName+"/classification labels="+labels);
    	File eventDir = uploadDir(meGroup, meEventGroup, meName);
    	if(!eventDir.isDirectory()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such event");

    	boolean anyTrue = false;
    	for (Map.Entry<String,Object> entry : labels.entrySet()) {
    		if(!entry.getKey().matches("[a-z0-9_]+") || entry.getKey().startsWith("me_"))
    			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad label: "+entry.getKey());
    		if(!(entry.getValue() instanceof Boolean))
    			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label "+entry.getKey()+" must be true or false");
    		anyTrue |= (Boolean)entry.getValue();
    	}
    	if(!anyTrue) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one label must be true");

    	File ollamaFile = new File(eventDir, "me_ollama.json");
    	Map<String,Object> old = readOllama(eventDir);
    	// never overwrite a classification we couldn't read, it may be the only copy of ollama's answer
    	if(old == null && ollamaFile.exists())
    		throw new ResponseStatusException(HttpStatus.CONFLICT, "can't read the event's me_ollama.json");

    	Map<String,Object> result = new LinkedHashMap<String,Object>(labels);
    	if(old != null && old.containsKey(OLLAMA_ORIGINAL)) {
    		result.put(OLLAMA_ORIGINAL, old.get(OLLAMA_ORIGINAL));
    		result.put(OLLAMA_PREVIOUS, ollamaLabels(old));
    	}
    	else {
    		result.put(OLLAMA_ORIGINAL, old == null ? null : ollamaLabels(old));
    	}
    	result.put(OLLAMA_CHANGED, Instant.now().toString());
    	result.put(OLLAMA_CHANGED_BY, CodeVersion.code());

    	// write it whole then swap it in, so a reader never sees half a file
    	File tmpFile = new File(eventDir, "me_ollama.json.tmp");
    	Files.writeString(tmpFile.toPath(), ollamaWriter.toJson(result), StandardCharsets.UTF_8);
    	Files.move(tmpFile.toPath(), ollamaFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    	Log.info("PutClassification: "+meGroup+"/"+meEventGroup+"/"+meName+" now="+labels);
    	labelIndex().refresh(eventDir);
    	return(result);
    }

    // The newest events of one camera, or of every camera, across dates, newest first. Always brief,
    // with each event's me_ollama.json added as "me_ollama".
    @GetMapping({"/catcam/latest", "/catcam/latest/{meGroup}"})
    public List<MeEvent> GetLatest(
    		@PathVariable(required = false) String meGroup,
    		@RequestParam(defaultValue = "10") int n,
    		HttpServletRequest request) {
    	Caller caller = Caller.of(request);
    	Log.info("/catcam/latest group="+meGroup+" n="+n);
    	n = capRecords(n);
    	acquireEventListPermit();
    	try {
    		List<File> groups = (meGroup == null)
    				? subDirsNewestFirst(uploadRoot().toFile()).stream()
    						.filter(g -> caller.canSee(g.getName())).collect(Collectors.toList())    // only the caller's cameras
    				: Collections.singletonList(uploadDir(meGroup));
    		List<MeEvent> list = new ArrayList<MeEvent>();
    		for (File groupDir : groups)
    			list.addAll(latestEvents(groupDir, n, caller));
    		// event names start with their date and time, so they sort across cameras too
    		list.sort(Comparator.comparing((MeEvent e) -> String.valueOf(e.get("me_name"))).reversed());
    		return(list.size() > n ? new ArrayList<MeEvent>(list.subList(0, n)) : list);
    	}
    	finally {
    		eventListPermits.release();
    	}
    }

    private List<MeEvent> latestEvents(File groupDir, int n, Caller caller) {
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	for (File dateDir : subDirsNewestFirst(groupDir)) {
    		for (File eventDir : subDirsNewestFirst(dateDir)) {
    			if(list.size() >= n) return(list);
    			Map<String,Object> ollama = readOllama(eventDir);
    			if(!caller.mayView(groupDir.getName(), ollamaLabels(ollama))) continue;
    			MeEvent meEvent = readMeData(eventDir);
    			if(meEvent == null) continue;
    			briefMeEvent(meEvent);
    			meEvent.put("me_ollama", ollama);
    			list.add(meEvent);
    		}
    	}
    	return(list);
    }

    // Is the server up, which build is it, where does it keep events, and the newest event per
    // camera. Cheap: it lists folders and reads no event files.
    @GetMapping("/catcam/health")
    public Map<String,Object> GetHealth() {
    	File root = uploadRoot().toFile();
    	Map<String,Object> groups = new LinkedHashMap<String,Object>();
    	List<File> groupDirs = subDirsNewestFirst(root);
    	Collections.reverse(groupDirs);    // A to Z
    	for (File groupDir : groupDirs) {
    		List<File> dates = subDirsNewestFirst(groupDir);
    		Map<String,Object> g = new LinkedHashMap<String,Object>();
    		g.put("dates", dates.size());
    		g.put("newest_date", dates.isEmpty() ? null : dates.get(0).getName());
    		String newestEvent = null;
    		for (File dateDir : dates) {
    			List<File> events = subDirsNewestFirst(dateDir);
    			if(!events.isEmpty()) { newestEvent = events.get(0).getName(); break; }
    		}
    		g.put("newest_event", newestEvent);
    		groups.put(groupDir.getName(), g);
    	}

    	Map<String,Object> result = new LinkedHashMap<String,Object>();
    	result.put("status", "ok");
    	result.put("code", CodeVersion.code());
    	result.put("version", CodeVersion.version());
    	result.put("build_time", buildProperties == null || buildProperties.getTime() == null ? null : buildProperties.getTime().toString());
    	result.put("upload_dir", root.getPath());
    	result.put("free_bytes", root.getUsableSpace());
    	result.put("total_bytes", root.getTotalSpace());
    	result.put("groups", groups);
    	result.put("auth", authConfig.describe(accessStore));
    	result.put("label_index", labelIndex().describe());
    	return(result);
    }

    // Upload one file of an event. image_dir non-empty: the file goes in the event's images/ folder
    // (old Pi clients send their frames this way).
    @PostMapping("/uploadme/{meGroup}/{meEventGroup}/{meName}")
    public UploadFileResponse uploadFile(@PathVariable String meGroup,
    		@PathVariable String meEventGroup,
    		@PathVariable String meName,
    		@RequestParam(value = "image_dir", defaultValue = "") String imageDir,
    		@RequestParam("file") MultipartFile file) {
        String saveFileName = fileStorageService.getFileName(file);
        String saveDir = meGroup+"/"+meEventGroup+"/"+meName+(imageDir.isEmpty() ? "" : "/images");
        uploadDir(saveDir, saveFileName);    // refuses names that leave the uploads folder
        fileStorageService.createSubDir(saveDir);
        String fileName = fileStorageService.storeFile(file, saveDir+"/"+saveFileName);

        Log.info("uploadme:fileName="+fileName);
        labelIndex().refresh(uploadDir(meGroup, meEventGroup, meName));    // a new event, or its me_ollama.json

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/catcam/data/")
                .path(fileName)
                .toUriString();

        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }

    // http://localhost:9090/catcam/data/pet_door/20200327/20200326_234655_0310/me_rep_image.jpg
    @GetMapping("/catcam/data/{meGroup}/{meGroupName}/{meEventName}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
    		@PathVariable String meGroup,
    		@PathVariable String meGroupName,
    		@PathVariable String meEventName,
    		@PathVariable String fileName, HttpServletRequest request) {
		return(serveFile(request, meGroup, meGroupName, meEventName, fileName));
    }

    @GetMapping("/catcam/data/{meGroup}/{meGroupName}/{meEventName}/{imageDir}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
    		@PathVariable String meGroup,
    		@PathVariable String meGroupName,
    		@PathVariable String meEventName,
    		@PathVariable String imageDir,
    		@PathVariable String fileName, HttpServletRequest request) {
		return(serveFile(request, meGroup, meGroupName, meEventName, imageDir, fileName));
    }

    private ResponseEntity<Resource> serveFile(HttpServletRequest request, String... names) {
    	uploadDir(names);    // refuses names that leave the uploads folder
    	// an animals-only caller gets a hidden event's files as if they weren't there (camera, date, event, file...)
    	if(!mayView(Caller.of(request), uploadDir(names[0], names[1], names[2])))
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such file");
        Resource resource = fileStorageService.loadFileAsResource(String.join("/", names));

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            Log.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

}
