#!/usr/bin/python

#python ./capture_motion.py -c pi_surv_config.json -o /home/mdm/storage/proc_images
import argparse
import warnings
import datetime
import imutils
import json
import time
import cv2
import os

# construct the argument parser and parse the arguments
ap = argparse.ArgumentParser()
ap.add_argument("-c", "--conf", required=True,
        help="path to the JSON configuration file")
ap.add_argument("-o", "--output", required=True,
	help="path to output directory")
#ap.add_argument("-f", "--fps", type=int, default=10,
#	help="FPS of output video")
ap.add_argument("-d", "--codec", type=str, default="mp4v",
	help="codec of output video") #MJPG for an .avi, mp4v for an mp4
ap.add_argument("-b", "--buffer-size", type=int, default=32,
	help="buffer size of video clip writer")
args = vars(ap.parse_args())

#####kcw = KeyClipWriter(bufSize=args["buffer_size"])

# filter warnings, load the configuration and initialize the Dropbox
# client
warnings.filterwarnings("ignore")
conf = json.load(open(args["conf"]))
client = None

avg = None
lastUploaded = datetime.datetime.now()
consecFrames = 0
motionCounter = 0

max_iamge_buffer  = 50
# capture frames from the camera
#for f in camera.capture_continuous(rawCapture, format="bgr", use_video_port=True):
average_files_window = []
counter = 0
not_occupied_counter = 0
occupied_counter = 0
cam = cv2.VideoCapture(0)
mirror = False
file_name = "cam"
while(True):
    ret_val, frame = cam.read()
    if mirror: frame = cv2.flip(frame, 1) 
	#full_file_name = relevant_path+file_name
	#file_size = (os.path.getsize(full_file_name))

	# resize the frame, convert it to grayscale, and blur it
	#frame = cv2.imread(full_file_name)
    if(True) :
        # grab the raw NumPy array representing the image and initialize
        # the timestamp and occupied/unoccupied text
        #frame = f.array
        orig = frame.copy()
        timestamp = datetime.datetime.now()
        text = "Unoccupied"
 
        # resize the frame, convert it to grayscale, and blur it
        frame = imutils.resize(frame, width=500)
        frame_copy = frame.copy()
        #cv2.putText(frame_copy, "counter "+str(counter), (10, 20),
        #        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
        #gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        #gray = cv2.GaussianBlur(gray, (21, 21), 0)
        # cv2.imshow('image',gray)
        # cv2.waitKey(1000)
        # cv2.destroyAllWindows()
        if(len(average_files_window)==0) : 
                average_files_window.append(frame_copy)
        avearage_past = None

        for one_past_image in reversed(average_files_window):
        #for past_image in (average_files_window):
                if avearage_past is None: 
                        avearage_past = one_past_image
                else :
                        avearage_past = cv2.addWeighted(avearage_past,0.9, one_past_image,0.1,0) 

        gray = cv2.cvtColor(frame_copy.copy(), cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)

        avg_gray = cv2.cvtColor(avearage_past.copy(), cv2.COLOR_BGR2GRAY)
        avg_gray = cv2.GaussianBlur(avg_gray, (21, 21), 0)
        avg = avg_gray.copy().astype("float")

        cv2.accumulateWeighted(gray, avg, 0.5)
        frameDelta = cv2.absdiff(gray, cv2.convertScaleAbs(avg))
        # threshold the delta image, dilate the thresholded image to fill
        # in holes, then find contours on thresholded image
        thresh = cv2.threshold(frameDelta, conf["delta_thresh"], 255,
                cv2.THRESH_BINARY)[1]
        thresh = cv2.dilate(thresh, None, iterations=2)
        import imutils
        cnts = cv2.findContours(thresh.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        print("*********if imutils.is_cv2()="+ str(imutils.is_cv2()))
        cnts = cnts[0] if imutils.is_cv2() else cnts[1]
         
        # loop over the contours
        for c in cnts:
                # if the contour is too small, ignore it
                if cv2.contourArea(c) < conf["min_area"]:
                        continue
 
                # compute the bounding box for the contour, draw it on the frame,
                # and update the text
                (x, y, w, h) = cv2.boundingRect(c)
                cv2.rectangle(frame, (x, y), (x + w, y + h), (0, 255, 0), 2)
                text = "Occupied"
 
        # draw the text and timestamp on the frame
        ts = timestamp.strftime("%A %d %B %Y %I:%M:%S%p")+":"+file_name
        cv2.putText(frame, "Room Status: {}".format(text), (10, 20),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
        cv2.putText(frame, ts, (10, frame.shape[0] - 10), cv2.FONT_HERSHEY_SIMPLEX,
                0.35, (0, 0, 255), 1)
        updateConsecFrames = True
        # check to see if the room is occupied

        im_v = cv2.hconcat([frame, avearage_past])
        #im_v = cv2.hconcat([gray,avg_gray])

        #all_past = cv2.hconcat(average_files_window)

        ##cv2.imshow('image',im_v)
        ##cv2.waitKey(100)
        
        #cv2.destroyAllWindows()
        status = "0"
        # if(len(average_files_window)<max_iamge_buffer) : 
        #         average_files_window.append(frame_copy)
        #         status = "1"
        # elif text != "Occupied" and (not_occupied_counter>100 or occupied_counter>100) :
        #         average_files_window.append(frame_copy)
        #         if(len(average_files_window)>max_iamge_buffer): del average_files_window[0]
        #        status = status +"2"
        if True :
                average_files_window.append(frame_copy)
                if(len(average_files_window)>max_iamge_buffer): del average_files_window[0]

        print(text+":working on elemnt "+str(counter)+" status="+status+" avg files="+str( len(average_files_window) ) +
                " not_occupied_counter="+str(not_occupied_counter)+
                " max_iamge_buffer="+str(max_iamge_buffer))#+" of "+len(file_names.size))
        if text != "Occupied" : occupied_counter = 0
        if text == "Occupied" :
                not_occupied_counter = 0
                occupied_counter = occupied_counter + 1
                motionCounter += 1
                if motionCounter >= conf["min_motion_frames"]:
                        import time
                        consecFrames = 0
                        #origFileName =  "Detection_" + time.strftime("%Y%m%d-%H%M%S") + ".jpg"
                        #cv2.imwrite('/home/pi/LocalDetection/' + origFileName, orig)
                        #print("Uploaded")
                        #if not kcw.recording:
                        if(False):
                                
                                timestamp = datetime.datetime.now()
                                # .avi for an avi (must have MJPG set in codec in args
                                p = "{}/{}.mp4".format(args["output"],timestamp.strftime("%Y%m%d-%H%M%S"))
                                kcw.start(p, cv2.VideoWriter_fourcc(*args["codec"]), conf["fps"])
                        motionCounter = 0
                #origFileName =  "Detection_" + time.strftime("%Y%m%d-%H%M%S") + ".jpg"
                #cv2.imwrite('/home/mdm/storage/proc_images/' + origFileName, im_v)

        
        if updateConsecFrames:
                consecFrames += 1#increment motion counter of frames wihtout motion

        ################kcw.update(frame)
        ####################if kcw.recording and consecFrames == args["buffer_size"]:
        if(True):
                import time

                #Takes a picture of the frame, but at the end of all of the 'commotion'
                #origFileName =  "Detection_" + time.strftime("%Y%m%d-%H%M%S") + ".jpg"
                #cv2.imwrite('/home/mdm/storage/proc_images/' + origFileName, orig)
                ################################ not writing becasue we are watching cv2.imwrite('/home/mdm/storage/proc_images/' + file_name, frame)
                ##################kcw.finish()
                #Wait 5 seconds to ensure file is written 100%
                #time.sleep(5)

                
                #path = "/{base_path}/{p}".format(base_path=conf["dropbox_base_path"], p=p)
                #dbx.files_upload(open(p, "rb").read(), path)


                #Use telegram to send the video to the group chat
                ###############bot.send_video(chat_id=-MYCHAT, video=open(p, 'rb'))


        # check to see if the frames should be displayed to screen
        
        #if conf["show_video"]:
        #        # display the security feed
        #        cv2.imshow("Security Feed", frame)
        #        key = cv2.waitKey(1) & 0xFF
 
                # # if the `q` key is pressed, break from the lop
                # if key == ord("q"):
                #         break
 
        
        counter = counter + 1    
        # clear the stream in preparation for the next frame
