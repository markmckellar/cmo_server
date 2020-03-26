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

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
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
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@RequestMapping("/catcam/movies")
    public List<MotionEvent> GetCatCamMovies(HttpServletRequest request) throws IOException  {
    	
    	List<MotionEvent> movieList = new ArrayList<MotionEvent>();
    	String uploadDir = fileStorageService.getFileStorageProperties().getUploadDir();
    	File dir = new File(uploadDir);

        // list the files using our FileFilter
        File[] files = dir.listFiles();

        for (File f : files)
        {
          //System.out.println("file: " + f.getName());
          //Log.info("file: " + f.getName());
        	String fileName = f.getName();
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
  
}

