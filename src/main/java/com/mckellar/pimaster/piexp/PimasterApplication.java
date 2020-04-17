package com.mckellar.pimaster.piexp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.reflect.TypeToken;
import com.mckellar.pimaster.piexp.fileioservice.FileStorageProperties;
import com.mckellar.pimaster.piexp.fileioservice.FileStorageService;
import com.mckellar.pimaster.piexp.fileioservice.UploadFileResponse;


import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	
	//@EnableWebSecurity
	//public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
	    http.cors().configurationSource(request -> new CorsConfiguration().applyPermitDefaultValues());
	    http.csrf().disable();
	}
	 
    @RequestMapping("/")
    String home() {
    	Log.info("I am at home");
        return "Hello, World!";
    }
   
    @RequestMapping("/hello2")
    String heeeyyy() {
    	Log.info("I am at heeeyyy");

        return "Heeeeeeeeeeeeeeeey";
    }
    
    @RequestMapping("/echotheid/{id}")
    String EchoId(@PathVariable Long id)  {
    	Log.info("I am at EchoId:"+id);

        return (id*2)+"";
    }
    
    @PostMapping("/eatarray")
    List<Object>  EchoId(@RequestBody List<Object> inList)  {
    	Log.info("eatarray:"+inList.toString());

        return inList;
    }
    
    @PostMapping("/eatmap")
    Map<Object,Object> EchoId(@RequestBody Map<Object,Object> inMap)  {
    	Log.info("eatarray:"+inMap.toString());

        return inMap;
    }
    
    
    
    class MotionEvent {
    	public String movieName;
    	public String movieUrl;
    	public Map<Object,Object> eventData;
    	
    	public MotionEvent(String movieName,String movieUrl) {
    		this.movieName = movieName;
    		this.movieUrl = movieUrl;
    		eventData = new HashMap<Object,Object>();
    	}
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

    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@RequestMapping("/catcam/meeventgroup/{meGroup}/{meName}")
    public List<MeEvent> GetMeEvent(
    		@PathVariable String meGroup,
    		@PathVariable String meName,
    		@RequestParam int howMayRecords,
    		@RequestParam int startRecord,
    		@RequestParam Double minimumDuration,
    		HttpServletRequest request) throws IOException  {
        Log.info("/catcam/meeventgroup");

    	List<MeEvent> list = new ArrayList<MeEvent>();
    	String uploadDir = fileStorageService.getFileStorageProperties().getUploadDir();
    	File dir = new File(uploadDir+"/"+meGroup+"/"+meName);

        List<File> fileList = Arrays.asList(dir.listFiles());        
        Collections.sort(fileList,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) {
					 int v = ((File)o1).getName().compareTo(
							 ((File)o2).getName() );				
					 return v;           
				 }
			}
        );
        Collections.reverse(fileList);
        
        long startTime = System.nanoTime();       
        for (int i=0;i<fileList.size();i++)
        {
        	// we got all we need :)
        	if(list.size()>=howMayRecords) break;
        	// skip if we have not hit the start record
        	if(i<startRecord) continue;

        	File f = fileList.get(i);
        	String jsonPath = f.getPath()+"/me_data.json";
        	
            Log.info("jsonPath="+jsonPath);

            try {

	        	String json = Files.readString(new File(jsonPath).toPath(), StandardCharsets.US_ASCII);
	     		Gson gson = new Gson();
				Type type = new TypeToken<MeEvent>() {}.getType();
				MeEvent meEvent = gson.fromJson(json,type);
				if(meEvent.hasMeTime())
					if(meEvent.getMeTime()>minimumDuration)
						list.add(meEvent);	
        	}
            catch(Exception e) {
                Log.warn("error reading in json file="+jsonPath+" error="+e.getMessage());

            }


        }
        Collections.sort(list,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) 
				 {
				 int v = ((MeEvent)o1).getMeName().compareTo(
						 ((MeEvent)o2).getMeName()
						 );				
				 return v;           
				
				     // it can also return 0, and 1
				 }
		        }
        );
        Collections.reverse(list);
        long elapsedTime = (System.nanoTime() - startTime)/1000000;
        Log.info("GetMeEventGroups: " + uploadDir+":list="+list.size()+" elapsedTime="+elapsedTime);

        return(list);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@RequestMapping("/catcam/megroups")
    public List<MeGroup> GetMeGroups(HttpServletRequest request) throws IOException  {
        Log.info("/catcam/megroups");

    	List<MeGroup> list = new ArrayList<MeGroup>();
    	String uploadDir = fileStorageService.getFileStorageProperties().getUploadDir();
    	File dir = new File(uploadDir);

        // list the files using our FileFilter
        File[] files = dir.listFiles();

        for (File f : files)
        {
        	MeGroup meGroup = new MeGroup(f.getName());
			list.add(meGroup);
        }
        Collections.sort(list,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) 
				 {
				 int v = ((MeGroup)o1).meGroup.compareTo(
						 ((MeGroup)o2).meGroup
						 );				
				 return v;           
				
				     // it can also return 0, and 1
				 }
		        }
        );
        //Collections.reverse(list);
        
        Log.info("GetMeGroups: " + uploadDir+":list="+list.size());

        return(list);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@RequestMapping("/catcam/meeventgroup/{meGroup}")
    public List<MeEventGroup> GetMeEventGroups(@PathVariable String meGroup,@RequestParam int howMayRecords,
    		HttpServletRequest request) throws IOException  {
        Log.info("/catcam/meeventgroup");

    	List<MeEventGroup> list = new ArrayList<MeEventGroup>();
    	String uploadDir = fileStorageService.getFileStorageProperties().getUploadDir();
    	File dir = new File(uploadDir+"/"+meGroup);

        // list the files using our FileFilter
        List<File> fileList = Arrays.asList(dir.listFiles());        
        Collections.sort(fileList,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) {
					 int v = ((File)o1).getName().compareTo(
							 ((File)o2).getName() );				
					 return v;           
				 }
			}
        );
        Collections.reverse(fileList);
        
        
        for (int i=0;i<fileList.size();i++)
        {
          	// we got all we need :)
        	if(list.size()>=howMayRecords) break;
        	File f = fileList.get(i);
        	MeEventGroup meEventGroup = new MeEventGroup(f.getName());
			list.add(meEventGroup);
        }
        Collections.sort(list,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) 
				 {
				 int v = ((MeEventGroup)o1).meEventGroup.compareTo(
						 ((MeEventGroup)o2).meEventGroup
						 );				
				 return v;           
				
				     // it can also return 0, and 1
				 }
		        }
        );
        Collections.reverse(list);
        
        Log.info("GetMeEventGroups: " + uploadDir+":list="+list.size());

        return(list);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@RequestMapping("/catcam/megroup/{me_group}")
    public List<MotionEvent> GetCatCamMovies(@PathVariable String meGroup,HttpServletRequest request) throws IOException  {
    	
    	List<MotionEvent> movieList = new ArrayList<MotionEvent>();
    	String uploadDir = fileStorageService.getFileStorageProperties().getUploadDir();
    	File dir = new File(uploadDir+"/"+meGroup);

        // list the files using our FileFilter
        File[] files = dir.listFiles();

        for (File f : files)
        {
          //System.out.println("file: " + f.getName());
          //Log.info("file: " + f.getName());
        	String fileName = f.getName()+"/me_data.json";
        	if(fileName.toLowerCase().endsWith(".json"))
        	{
        		String baseName = fileName.substring(0,fileName.length()-5);
				MotionEvent me = new MotionEvent(
					baseName,
					  request.getRequestURL().toString().replace("movies","movie")+"/"+baseName+".mp4"
				);
				String json = Files.readString(f.toPath(), StandardCharsets.US_ASCII);

				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(f.getPath()));
				Object data = gson.fromJson(reader, Object.class);

				me.eventData.put("meData",data);
				
				movieList.add(me);
			
        	}
        }
        Collections.sort(movieList,
        		new Comparator() {
				
				 public int compare(Object o1, Object o2) 
				 {
				 int v = ((MotionEvent)o1).movieName.compareTo(
						 ((MotionEvent)o2).movieName
						 );				
				 return v;           
				
				     // it can also return 0, and 1
				 }
		        }
        );
        Collections.reverse(movieList);
        
        Log.info("uploadDir: " + uploadDir+":movieList="+movieList.size());

        return(movieList);
    }
    
    @Autowired
    private FileStorageService fileStorageService;
    
    
    @PostMapping("/uploadfile")
    public UploadFileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        Log.info("uploading a file starting...");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;
        //String saveFileName = sdf.format(new Date())+"_"+fileStorageService.getFileName(file);
        String saveFileName =fileStorageService.getFileName(file);

        String fileName = sdf.format(new Date())+"_"+fileStorageService.storeFile(file,saveFileName);
       //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;

        Log.info("uploading a file:"+fileName);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();

        Log.info("uploadfile:finalFileName="+fileName);

        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }
    
    @PostMapping("/uploadme/{meGroup}/{meEventGroup}/{meName}")
    public UploadFileResponse uploadFile(@PathVariable String meGroup,
    		@PathVariable String meEventGroup,
    		@PathVariable String meName,
    		@RequestParam("image_dir") String imageDir,
    		@RequestParam("file") MultipartFile file) {
        Log.info("uploading a file starting...");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;
        //String saveFileName = sdf.format(new Date())+"_"+fileStorageService.getFileName(file);
        String saveFileName =fileStorageService.getFileName(file);
        
        String saveDir = meGroup+"/"+meEventGroup+"/"+meName;
        String saveDirIages = saveDir+"/images";
        fileStorageService.createSubDir(saveDirIages);
        
        String storeFileName =
        		(imageDir.isEmpty()) ?
        				saveDir+"/"+saveFileName
        				: saveDirIages+"/"+saveFileName;
        String fileName = sdf.format(new Date())+"_"+fileStorageService.storeFile(file,storeFileName);
        //fileName = meGroup+"/"+meEventGroup+"/"+meName+"/"+fileName;
       //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;

        Log.info("uploading a file:"+fileName);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();

        Log.info("uploadfile:finalFileName="+fileName);

        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }
    
    
    @GetMapping("/catcam/movie/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(fileName);

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
    // http://localhost:8080/catcam/data/pet_door/20200327/20200326_234655_0310/me_rep_image.jpg
    
    @GetMapping("/catcam/data/{meGroup}/{meGroupName}/{meEventName}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
    		@PathVariable String meGroup,
    		@PathVariable String meGroupName,
    		@PathVariable String meEventName,
    		@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
		fileName = meGroup+"/"+meGroupName+"/"+meEventName+"/"+fileName;
        Resource resource = fileStorageService.loadFileAsResource(fileName);

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
    
    @GetMapping("/catcam/data/{meGroup}/{meGroupName}/{meEventName}/{imageDir}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
    		@PathVariable String meGroup,
    		@PathVariable String meGroupName,
    		@PathVariable String meEventName,
    		@PathVariable String imageDir,
    		@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
		fileName = meGroup+"/"+meGroupName+"/"+meEventName+"/"+imageDir+"/"+fileName;
        Resource resource = fileStorageService.loadFileAsResource(fileName);

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

