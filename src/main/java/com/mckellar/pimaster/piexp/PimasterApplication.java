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

    @RequestMapping("/")
    String home() {
    	Log.info("I am at home");
        return "Hello, World!";
    }

    @Autowired
    private FileStorageService fileStorageService;

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

    class MeGroup {
      	public String meGroup;

    	public MeGroup(String meGroup) {
    		this.meGroup = meGroup;
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
        	return(readMeEvents(meGroup, meName, howMayRecords, startRecord, minimumDuration, brief));
        }
        finally {
        	eventListPermits.release();
        }
    }

    // The events of one date, newest first. startRecord counts event folders, read or not.
    private List<MeEvent> readMeEvents(String meGroup, String meName, int howMayRecords,
    		int startRecord, Double minimumDuration, boolean brief) {
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	List<File> eventDirs = subDirsNewestFirst(uploadDir(meGroup, meName));

        long startTime = System.nanoTime();
        for (int i=startRecord;i<eventDirs.size();i++)
        {
        	// we got all we need :)
        	if(list.size()>=howMayRecords) break;
        	MeEvent meEvent = readMeData(eventDirs.get(i));
        	if(longEnough(meEvent, minimumDuration))
        		list.add(brief ? briefMeEvent(meEvent) : meEvent);
        }
        long elapsedTime = (System.nanoTime() - startTime)/1000000;
        Log.info("GetMeEventGroups: " + meGroup+"/"+meName+":list="+list.size()+" elapsedTime="+elapsedTime);

        return(list);
    }

    // Search every date for events that ollama tagged with a label (cat, dog, person, ...), newest first.
    // For the next page pass the me_name of the last event received as "before".
	@RequestMapping("/catcam/search/{meGroup}")
    public List<MeEvent> SearchMeEvents(
    		@PathVariable String meGroup,
    		@RequestParam String label,
    		@RequestParam int howMayRecords,
    		@RequestParam Double minimumDuration,
    		@RequestParam(required = false) String before,
    		@RequestParam(defaultValue = "false") boolean brief,
    		HttpServletRequest request) throws IOException  {
        Log.info("/catcam/search label="+label+" before="+before);
        howMayRecords = capRecords(howMayRecords);
        acquireEventListPermit();
        try {
        	return(searchMeEvents(meGroup, label, howMayRecords, minimumDuration, before, brief));
        }
        finally {
        	eventListPermits.release();
        }
    }

    private List<MeEvent> searchMeEvents(String meGroup, String label, int howMayRecords,
    		Double minimumDuration, String before, boolean brief) {
    	List<MeEvent> list = new ArrayList<MeEvent>();

    	// event names start with their date (yyyyMMdd_...) so whole dates after "before" can be skipped
    	String beforeDate = (before != null && before.length() >= 8) ? before.substring(0,8) : null;
    	int scanned = 0;
        long startTime = System.nanoTime();

        for (File dateDir : subDirsNewestFirst(uploadDir(meGroup)))
        {
        	if(list.size()>=howMayRecords) break;
        	if(beforeDate != null && dateDir.getName().compareTo(beforeDate) > 0) continue;

        	for (File eventDir : subDirsNewestFirst(dateDir))
        	{
        		if(list.size()>=howMayRecords) break;
        		if(before != null && eventDir.getName().compareTo(before) >= 0) continue;

        		scanned++;
        		if(!hasOllamaLabel(readOllama(eventDir),label)) continue;

        		MeEvent meEvent = readMeData(eventDir);
        		if(longEnough(meEvent, minimumDuration))
        			list.add(brief ? briefMeEvent(meEvent) : meEvent);
        	}
        }

        long elapsedTime = (System.nanoTime() - startTime)/1000000;
        Log.info("SearchMeEvents: label="+label+" list="+list.size()+" scanned="+scanned+" elapsedTime="+elapsedTime);

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

    	List<MeGroup> list = new ArrayList<MeGroup>();
    	for (File f : subDirsNewestFirst(uploadRoot().toFile()))
    		list.add(new MeGroup(f.getName()));
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
    		@PathVariable String meName) throws IOException {
    	Log.info("/catcam/event/"+meGroup+"/"+meEventGroup+"/"+meName);
    	File eventDir = uploadDir(meGroup, meEventGroup, meName);
    	if(!eventDir.isDirectory()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such event");

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

    // Just the labels of a me_ollama.json, without the backups
    private static Map<String,Object> ollamaLabels(Map<String,Object> ollama) {
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
    	return(result);
    }

    // The newest events of one camera, or of every camera, across dates, newest first. Always brief,
    // with each event's me_ollama.json added as "me_ollama".
    @GetMapping({"/catcam/latest", "/catcam/latest/{meGroup}"})
    public List<MeEvent> GetLatest(
    		@PathVariable(required = false) String meGroup,
    		@RequestParam(defaultValue = "10") int n) {
    	Log.info("/catcam/latest group="+meGroup+" n="+n);
    	n = capRecords(n);
    	acquireEventListPermit();
    	try {
    		List<File> groups = (meGroup == null)
    				? subDirsNewestFirst(uploadRoot().toFile())
    				: Collections.singletonList(uploadDir(meGroup));
    		List<MeEvent> list = new ArrayList<MeEvent>();
    		for (File groupDir : groups)
    			list.addAll(latestEvents(groupDir, n));
    		// event names start with their date and time, so they sort across cameras too
    		list.sort(Comparator.comparing((MeEvent e) -> String.valueOf(e.get("me_name"))).reversed());
    		return(list.size() > n ? new ArrayList<MeEvent>(list.subList(0, n)) : list);
    	}
    	finally {
    		eventListPermits.release();
    	}
    }

    private List<MeEvent> latestEvents(File groupDir, int n) {
    	List<MeEvent> list = new ArrayList<MeEvent>();
    	for (File dateDir : subDirsNewestFirst(groupDir)) {
    		for (File eventDir : subDirsNewestFirst(dateDir)) {
    			if(list.size() >= n) return(list);
    			MeEvent meEvent = readMeData(eventDir);
    			if(meEvent == null) continue;
    			briefMeEvent(meEvent);
    			meEvent.put("me_ollama", readOllama(eventDir));
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
