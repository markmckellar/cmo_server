# cmo_server

Server end for the motion cameras ("catcam"): the pet door Pi and the porch camera, both running
cmo_watch from [cmo_client](https://github.com/markmckellar/cmo_client), record motion events and upload them here; the server stores them on
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
| `server.address` | `0.0.0.0` | Every network interface. `127.0.0.1` keeps it to this machine |
| `file.upload-dir` | `./local/uploads` | Where events are stored; created if missing |
| `spring.servlet.multipart.max-file-size` | `200MB` | Max size of a single upload |

Any property can be overridden at launch without rebuilding, e.g.:

```sh
java -jar target/cmo_server-0.0.1-SNAPSHOT.jar --server.port=9191 --file.upload-dir=/data/catcam
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
        images/              individual frames (old Pi clients only)
```

An unknown camera or date gives an empty list, not an error.

## Endpoints

All examples assume the server is at `http://localhost:9090`.

### Checking the server and uploads

#### `GET /catcam/health`

Is the server up, which build is it, where does it keep events, and each camera's newest event.
Cheap: it only lists folders. Use it to check that a camera or a test points at the right server.

```sh
curl http://localhost:9090/catcam/health
# {"status":"ok","version":"0.0.1-SNAPSHOT","build_time":"2026-10-11T23:20:25Z",
#  "upload_dir":"/data/catcam","free_bytes":...,"total_bytes":...,
#  "groups":{"pet_door":{"dates":420,"newest_date":"20261011","newest_event":"20261011_080000_0002"}, ...}}
```

#### `GET /catcam/latest?n=N` and `GET /catcam/latest/{meGroup}?n=N`

The newest events of every camera (or one), across dates, newest first. `n` defaults to 10
(at most 200). Always brief (see [Large responses](#large-responses)), with each event's
`me_ollama.json` added as `me_ollama`.

```sh
curl "http://localhost:9090/catcam/latest?n=3"
# [{"me_name":"20261011_152738_0000","me_group":"porch",...,"me_ollama":{"dog":true,...}}, ...]
```

#### `GET /catcam/event/{meGroup}/{meEventGroup}/{meName}`

Everything about one event: its files and sizes, `me_ollama.json`, `me_data.json` (brief), and
`complete`, which is true when `me_data.json` and the movie and picture it names are all there.
404 if there is no such event. Use it to check that an upload fully landed.

```sh
curl http://localhost:9090/catcam/event/porch/20261011/20261011_152738_0000
# {"me_group":"porch","me_event_group":"20261011","me_name":"20261011_152738_0000","complete":true,
#  "files":{"me_data.json":845,"me_movie.mp4":1430736,"me_ollama.json":119,"me_rep_image.jpg":119646},
#  "me_ollama":{...},"me_data":{...}}
```

#### `DELETE /catcam/event/{meGroup}/{meEventGroup}/{meName}`

Deletes one event: its folder and every file in it. If that leaves the date folder empty, the
date folder goes too. 404 if there is no such event. There is no undo, and no login: anyone who
can reach the server can do this. The web client asks for confirmation first.

```sh
curl -X DELETE http://localhost:9090/catcam/event/porch/20261011/20261011_152738_0000
# {"me_group":"porch","me_event_group":"20261011","me_name":"20261011_152738_0000","deleted":true}
```

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
| `brief` | Optional, default `false`. See [Large responses](#large-responses) |

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
| `brief` | Optional, default `false`. See [Large responses](#large-responses) |

```sh
curl "http://localhost:9090/catcam/search/pet_door?label=cat&howMayRecords=20&minimumDuration=0"
curl "http://localhost:9090/catcam/search/pet_door?label=cat&howMayRecords=20&minimumDuration=0&before=20200326_234655_0310"
```

#### Large responses

Both event list endpoints return each event's whole `me_data.json`. That's about 80KB
per event, mostly `me_delta_array`, and the server builds the whole response in memory.
Asking for thousands of events at once has run the server out of memory and hung it.
To prevent that:

- `howMayRecords` is capped at 200. Page through more with `startRecord` or `before`.
- Only 4 of these requests run at once. Others wait up to 10 seconds and then get
  `503 Service Unavailable`.
- `brief=true` replaces `me_delta_array` with `me_delta_count`, `me_first_event_time`
  and `me_last_event_time`. Use it when you only need event names or a summary.

```sh
curl "http://localhost:9090/catcam/search/pet_door?label=cat&howMayRecords=1&minimumDuration=0&brief=true"
# [{"me_name": ..., "me_time": ..., "me_delta_count": <frames>,
#   "me_first_event_time": <first event_time_iso>, "me_last_event_time": <last event_time_iso>, ...}]
```

### Downloading files

#### `GET /catcam/data/{meGroup}/{meEventGroup}/{meName}/{fileName}`
#### `GET /catcam/data/{meGroup}/{meEventGroup}/{meName}/{imageDir}/{fileName}`

Downloads a file from an event directory, or from a subdirectory such as `images/`.

```sh
curl -O http://localhost:9090/catcam/data/pet_door/20200327/20200326_234655_0310/me_rep_image.jpg
curl -O http://localhost:9090/catcam/data/pet_door/20200327/20200326_234655_0310/images/frame_0001.jpg
```

### Uploading

#### `POST /uploadme/{meGroup}/{meEventGroup}/{meName}`

Used by the cameras to upload an event's files. Multipart form fields:

| Field | Meaning |
|---|---|
| `file` | The file to upload |
| `image_dir` | Optional. Empty or left out: stored in the event directory. Any other value: stored in the event's `images/` subdirectory |

```sh
# event metadata
curl -F "image_dir=" -F "file=@me_data.json" \
  http://localhost:9090/uploadme/pet_door/20200327/20200326_234655_0310
# a frame
curl -F "image_dir=images" -F "file=@frame_0001.jpg" \
  http://localhost:9090/uploadme/pet_door/20200327/20200326_234655_0310
```

Returns `{"fileName", "fileDownloadUri", "fileType", "size"}`, where `fileDownloadUri` is the file's `/catcam/data/...` link.

## Tests

`mvn clean package` runs the endpoint tests (`src/test/`) against events in `target/test-uploads/`,
so they never touch real data.
