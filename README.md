# piexp
A prototype for the server end of a controlled network of pi devices 

To run : 
* ./gradlew botRun

To test : 
* curl http://localhost:8080/echotheid/23423432
* curl --header "Content-Type: application/json" --request POST --data '{"username":"xyz","password":"xyz"}'  http://localhost:8080/eatmap
* curl --header "Content-Type: application/json" --request POST --data '[1,2,3,"a","b","c"]'  http://localhost:8080/eatarray

