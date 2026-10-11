# cmo_server

Server end for a network of Raspberry Pi motion cameras ("catcam"). The Pi clients
(`src/client/`) record motion events and upload them here; the server stores them on
disk and serves them, plus a web client (`src/main/resources/static/cmo_client/`), for
browsing and searching events.

## Requirements

- Java 17
- Maven 3.6+

On Ubuntu/Debian both come from the package manager:

```sh
sudo apt install openjdk-17-jdk maven
```

## Build and run

```sh
mvn clean package
java -jar target/cmo_server-0.0.1-SNAPSHOT.jar
```

For development you can also run straight from source with `mvn spring-boot:run`.

## Configuration

Settings live in `src/main/resources/application.properties`:

| Property | Default | Notes |
|---|---|---|
| `server.port` | `9090` | |
| `server.address` | `192.168.1.177` | Change to your host's IP, or remove to listen on all interfaces |
| `file.upload-dir` | `./local/uploads` | Where events are stored; created if missing |
| `spring.servlet.multipart.max-file-size` | `200MB` | Max size of a single upload |

Any property can be overridden at launch without rebuilding, e.g.:

```sh
java -jar target/cmo_server-0.0.1-SNAPSHOT.jar --server.address=0.0.0.0 --file.upload-dir=/data/catcam
```

## Web client

The bundled client is served at `http://<host>:9090/cmo_client/index.html`.

## Data layout

Motion events are stored under the upload directory as:

```
<upload-dir>/
  <meGroup>/                 camera / location, e.g. pet_door
    <meEventGroup>/          date, yyyyMMdd, e.g. 20200327
      <meName>/              event, yyyyMMdd_HHmmss_NNNN, e.g. 20200326_234655_0310
        me_data.json         event metadata (must contain me_name and me_time)
        me_ollama.json       optional classification, e.g. {"cat": true, "dog": false}
        me_rep_image.jpg     representative image, movie, etc.
        images/              individual frames
```

## Endpoints

All examples assume the server is at `http://localhost:9090`.

### Browsing events

#### `GET /catcam/megroups`

Lists the event groups (top-level directories, e.g. cameras), sorted by name.

```sh
curl http://localhost:9090/catcam/megroups
# [{"meGroup":"pet_door"}, ...]
```

#### `GET /catcam/meeventgroup/{meGroup}?howMayRecords=N`

Lists the date directories in a group, newest first, up to `howMayRecords`.

```sh
curl "http://localhost:9090/catcam/meeventgroup/pet_door?howMayRecords=10"
# [{"meEventGroup":"20200327"}, {"meEventGroup":"20200326"}, ...]
```

#### `GET /catcam/meeventgroup/{meGroup}/{meEventGroup}?howMayRecords=N&startRecord=S&minimumDuration=D`

Returns the `me_data.json` contents of events for one date, newest first.

| Param | Meaning |
|---|---|
| `howMayRecords` | Max events to return |
| `startRecord` | Number of event directories to skip (for paging) |
| `minimumDuration` | Only return events whose `me_time` is greater than this (seconds) |

```sh
curl "http://localhost:9090/catcam/meeventgroup/pet_door/20200327?howMayRecords=20&startRecord=0&minimumDuration=0"
```

#### `GET /catcam/search/{meGroup}?label=L&howMayRecords=N&minimumDuration=D[&before=meName]`

Searches across all dates for events whose `me_ollama.json` has `label` set to `true`
(case-insensitive, e.g. `cat`, `dog`, `person`). Returns `me_data.json` contents, newest
first.

| Param | Meaning |
|---|---|
| `label` | Classification to match |
| `howMayRecords` | Max events to return |
| `minimumDuration` | Only return events whose `me_time` is greater than this |
| `before` | Optional. For the next page, pass the `me_name` of the last event received |

```sh
curl "http://localhost:9090/catcam/search/pet_door?label=cat&howMayRecords=20&minimumDuration=0"
curl "http://localhost:9090/catcam/search/pet_door?label=cat&howMayRecords=20&minimumDuration=0&before=20200326_234655_0310"
```

### Downloading files

#### `GET /catcam/data/{meGroup}/{meEventGroup}/{meName}/{fileName}`
#### `GET /catcam/data/{meGroup}/{meEventGroup}/{meName}/{imageDir}/{fileName}`

Downloads a file from an event directory, or from a subdirectory such as `images/`.

```sh
curl -O http://localhost:9090/catcam/data/pet_door/20200327/20200326_234655_0310/me_rep_image.jpg
curl -O http://localhost:9090/catcam/data/pet_door/20200327/20200326_234655_0310/images/frame_0001.jpg
```

#### `GET /catcam/movie/{fileName}`

Downloads a file from the top level of the upload directory.

### Uploading

#### `POST /uploadme/{meGroup}/{meEventGroup}/{meName}`

Used by the Pi clients to upload an event's files. Multipart form fields:

| Field | Meaning |
|---|---|
| `file` | The file to upload |
| `image_dir` | Empty string to store in the event directory; any non-empty value stores it in the event's `images/` subdirectory |

```sh
# event metadata
curl -F "image_dir=" -F "file=@me_data.json" \
  http://localhost:9090/uploadme/pet_door/20200327/20200326_234655_0310
# a frame
curl -F "image_dir=images" -F "file=@frame_0001.jpg" \
  http://localhost:9090/uploadme/pet_door/20200327/20200326_234655_0310
```

Returns `{"fileName", "fileDownloadUri", "fileType", "size"}`.

#### `POST /uploadfile`

Uploads a single file to the top level of the upload directory.

```sh
curl -F "file=@helloworld.jpeg" http://localhost:9090/uploadfile
```

### Test / debug endpoints

| Endpoint | Behavior |
|---|---|
| `GET /` | Returns `Hello, World!` |
| `GET /hello2` | Returns `Heeeeeeeeeeeeeeeey` |
| `GET /echotheid/{id}` | Returns `id * 2` |
| `POST /eatarray` | Echoes a JSON array back |
| `POST /eatmap` | Echoes a JSON object back |

```sh
curl http://localhost:9090/echotheid/21
curl -H "Content-Type: application/json" -d '[1,2,3,"a"]' http://localhost:9090/eatarray
curl -H "Content-Type: application/json" -d '{"key1":"one"}' http://localhost:9090/eatmap
```
