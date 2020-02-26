package com.mckellar.pimaster.piexp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.mckellar.pimaster.piexp.fileioservice.FileStorageProperties;
import com.mckellar.pimaster.piexp.fileioservice.FileStorageService;
import com.mckellar.pimaster.piexp.fileioservice.UploadFileResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@SpringBootApplication
@RestController
@EnableConfigurationProperties({
    FileStorageProperties.class
})
public class PimasterApplication {
	
	
    private static final Logger Log = LoggerFactory.getLogger(PimasterApplication.class);



	public static void main(String[] args) {
		SpringApplication.run(PimasterApplication.class, args);
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
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @PostMapping("/uploadfile")
    public UploadFileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;
        String saveFileName = sdf.format(new Date())+"_"+fileStorageService.getFileName(file);

        String fileName = sdf.format(new Date())+"_"+fileStorageService.storeFile(file,saveFileName);
       //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String finalFileName = sdf.format(new Date())+"_"+fileName;

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();

        Log.info("uploadfile:finalFileName="+fileName);

        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }
    

}
