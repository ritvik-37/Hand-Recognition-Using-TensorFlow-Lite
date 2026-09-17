# Hand Gesture Recognition with MediaPipe

A real-time hand gesture recognition system. A Java Swing client captures webcam frames and
streams them over TCP to a Python server, which runs MediaPipe's gesture recognizer and sends
the predicted gesture back. Every recognized gesture is logged to a local SQLite database and
exposed over a small HTTP endpoint.

## Recognized gestures

The bundled model is MediaPipe's stock gesture recognizer. It classifies eight categories:

`None`, `Closed_Fist`, `Open_Palm`, `Pointing_Up`, `Thumb_Down`, `Thumb_Up`, `Victory`, `ILoveYou`

It is **not** an ASL alphabet model — it does not recognize letters. Recognizing A–Z would
require training a separate 26-class classifier.

## Features

- **Real-time prediction:** webcam feed with a live gesture label, updated five times a second.
- **Client-server architecture:** the Java client keeps one TCP connection open for the session;
  the Python server loads the model once at startup and shares it across clients.
- **Prediction logging:** each gesture change is written to `handsigns.db`.
- **History API:** `GET http://127.0.0.1:5001/history?limit=N` returns recent predictions as JSON.

## Technologies Used

**Backend (Python)**
- **MediaPipe Tasks** — the gesture recognizer (`GestureRecognizer`, IMAGE running mode).
- **OpenCV** — JPEG decoding and color conversion.
- **Flask** — serves the prediction history endpoint.
- **socket / threading** — TCP server for the Java client.
- **SQLite** — prediction history storage.

**Frontend (Java)**
- **Java Swing** — the GUI.
- **OpenCV (Java bindings)** — webcam capture and JPEG encoding.

## Setup

### 1. Python environment

MediaPipe ships wheels for **Python 3.9–3.12 only**. Newer interpreters (3.13, 3.14) will fail
at `pip install mediapipe`, so create the virtual environment from a 3.11 or 3.12 interpreter
explicitly rather than relying on whatever `python` resolves to:

```bash
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
```

### 2. OpenCV for Java

The Java client needs both the OpenCV jar and its native DLL — neither is bundled here.

1. Download the OpenCV Windows release from <https://opencv.org/releases/> and extract it
   (these instructions assume `C:\opencv`).
2. In IntelliJ, add `C:\opencv\build\java\opencv-<version>.jar` as a module library
   (**File → Project Structure → Modules → Dependencies → + → JARs or directories**).
3. Add the native library path to the VM options of the `SignClient` and `CameraTest` run
   configurations (**Run → Edit Configurations → Modify options → Add VM options**):

   ```
   -Djava.library.path="C:\opencv\build\java\x64"
   ```

   The jar version and the DLL must come from the same OpenCV release, or `System.loadLibrary`
   fails at startup.

### 3. JDK

The IntelliJ project is configured for **JDK 24** (`.idea/misc.xml`). The run configurations also
pass `--enable-native-access=ALL-UNNAMED`, which requires JDK 22 or newer — on an older JDK,
remove that flag or the VM refuses to start.

## How to Run

1. **Start the Python server** — double-click `start_server.bat`, or:

   ```bash
   .venv\Scripts\python server.py
   ```

   It listens on `127.0.0.1:5000` for the Java client and serves history on port `5001`.
   Both bind to localhost only.

2. **Run the Java client** — run `SignClient` from IntelliJ. The window opens; click
   **Start Recognition** to begin.

3. **Check the history** (optional):

   ```bash
   curl http://127.0.0.1:5001/history?limit=10
   ```

### Standalone Python GUI

`src/hand_recognition_gui.py` is a self-contained Tkinter alternative that does capture and
recognition in one process — no Java client, no server. It is a separate demo, not part of the
client-server flow above.

## Project Structure

```
.
├── .gitattributes
├── .gitignore
├── Hand Recognition Using TensorFlow LITE.iml
├── LICENSE
├── README.md
├── gesture_recognizer.task     MediaPipe model bundle
├── handsigns.db                SQLite prediction history (created on first run)
├── requirements.txt
├── server.py                   Python backend: TCP server + Flask history API
├── start_server.bat
└── src/
    ├── CameraTest.java         Webcam smoke test
    ├── SignClient.java         Java Swing client
    └── hand_recognition_gui.py Standalone Tkinter GUI
```

`gesture_recognizer.task` is a MediaPipe task bundle (a zip containing `hand_landmarker.task`
and `hand_gesture_recognizer.task`), not a raw `.tflite` flatbuffer.

## Known issues

- The IntelliJ Python run configuration named "server.py" points at a nonexistent
  `out/production/.../sign_server.py`. Repoint it at `server.py` in the project root, or just use
  `start_server.bat`.
- `src/hand_recognition_gui.py` feeds wall-clock milliseconds to `recognize_async`, which
  MediaPipe requires to be strictly increasing; it can raise on fast machines.

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
