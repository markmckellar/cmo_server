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
        me_ollama.json       optional classification, e.g. {"cat": true, "dog": false}; see PUT .../classification
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
# {"status":"ok","code":"cmo_server:1.0.0","version":"1.0.0","build_time":"2026-10-11T23:20:25Z",
#  "upload_dir":"/data/catcam","free_bytes":...,"total_bytes":...,
#  "groups":{"pet_door":{"dates":420,"newest_date":"20261011","newest_event":"20261011_080000_0002"}, ...}}
```

#### `GET /catcam/version`

This server's version (see [Versions](#versions)), also logged once at startup (`running cmo_server:1.0.0`).

```sh
curl http://localhost:9090/catcam/version
# {"code":"cmo_server:1.0.0","version":"1.0.0","build_time":"2026-10-14T22:08:18.898Z"}
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

#### `PUT /catcam/event/{meGroup}/{meEventGroup}/{meName}/classification`

Changes an event's classification (its `me_ollama.json`) to the labels in the body. Every value
must be `true` or `false`, and at least one must be `true`, or it is a 400. 404 if there is no
such event. The web client does this when you tap a classification on an event.

Nothing is lost. The first change keeps what ollama said as `me_ollama_original` (`null` if
ollama never classified the event). Each later change keeps the classification it replaces as
`me_ollama_previous`. `me_ollama_changed` is when it was last changed, and `me_ollama_changed_by` the
server code that changed it (`cmo_server:1.0.0`). Keys starting with `me_` are never labels. Returns the new `me_ollama.json`.

```sh
curl -X PUT -H 'Content-Type: application/json' \
  -d '{"person": true, "legs": true, "cat": false, "delivery": true}' \
  http://localhost:9090/catcam/event/porch/20261011/20261011_152738_0000/classification
# {"person":true,"legs":true,"cat":false,"delivery":true,
#  "me_ollama_original":{"legs":false,"person":true,"cat":false},
#  "me_ollama_changed":"2026-10-13T23:33:55.521Z","me_ollama_changed_by":"cmo_server:1.0.0"}
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

## Versions

Versioned the same way as every cmo repo (see cmo_ollama's README): `cmo_server:1.3.0` on the `v1.3.0`
release, `1.3.0+4` four commits after it, `1.3.0+4.dirty` with uncommitted changes too. The server runs
from its git checkout, so `CodeVersion` asks `git describe` at startup; without git it uses the pom's
version, which `release.bash` sets to the tag's. To release, commit everything and run
`./release.bash patch|minor|major [message]`: it sets the pom's `<version>`, commits that, and tags it.

## Tests

`mvn clean package` runs the endpoint tests (`src/test/`) against events in `target/test-uploads/`,
so they never touch real data.
