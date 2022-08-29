# piexp
A prototype for the server end of a controlled network of pi devices 

To run : 
* ./gradlew botRun

If you need to get mvn :
* wget https://mirrors.estointernet.in/apache/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
* tar -xvf apache-maven-3.6.3-bin.tar.gz
* sudo mv apache-maven-3.6.3 /opt/

To run vina maven :
* mvn spring-boot:run

To test : 
* curl http://localhost:8080/echotheid/23423432
* curl --header "Content-Type: application/json" --request POST --data '{"key1":"one","key2":"two"}'  http://localhost:8080/eatmap
* curl --header "Content-Type: application/json" --request POST --data '[1,2,3,"a","b","c"]'  http://localhost:8080/eatarray
* curl -F "file=@/home/mdm/Downloads/helloworld.jpeg"  http://localhost:8080/uploadfile

