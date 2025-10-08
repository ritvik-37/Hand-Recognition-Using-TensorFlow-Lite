# Hand Sign Recognition using TensorFlow Lite

This project is a real-time hand sign recognition system that uses a machine learning model to predict hand gestures captured from a webcam. It features a Python backend for processing images and making predictions, and a Java Swing GUI for the user interface.

## Features

- **Real-Time Hand Sign Prediction:** Captures video from a webcam and displays the predicted hand sign in real-time.
- **Client-Server Architecture:** A Java client captures and sends video frames to a Python server for prediction.
- **Prediction Logging:** The Python server logs every recognized sign into a local SQLite database.

## Technologies Used

**Backend (Python)**
- **TensorFlow Lite:** For running the hand sign detection model.
- **OpenCV:** For image processing and handling video frames.
- **Flask:** For creating an HTTP server to potentially serve prediction history.
- **Socket:** For TCP communication between the server and the Java client.
- **SQLite:** For storing prediction history.

**Frontend (Java)**
- **Java Swing:** For the graphical user interface.
- **OpenCV:** For accessing the webcam and capturing video frames.

## Setup and Installation

### Prerequisites

- **Java:** JDK 8 or newer.
- **Python:** Python 3.6 or newer.
- **OpenCV:** Must be installed and configured for both Python and Java.

### Dependencies

1. **Python Dependencies**
   Navigate to the project directory and run:
   ```bash
   pip install opencv-python numpy tensorflow flask
   ```
   *(Note: `tflite_runtime` can be used as a smaller alternative to the full `tensorflow` package.)*

2. **Java Dependencies (OpenCV)**
   Ensure the OpenCV library is correctly configured in your Java project's IDE.

## How to Run

1. **Start the Python Server:**
   Open a terminal in the project root and execute:
   ```bash
   python server.py
   ```
   The server will start and listen for predictions and HTTP requests.

2. **Run the Java Client:**
   Open the project in your Java IDE (e.g., IntelliJ IDEA) and run the `SignClient.java` file. The application window will open, display the webcam feed, and show predictions.

## Project Structure

```
.
├───.gitignore
├───Hand Recognition Using TensorFlow LITE.iml
├───labels.txt
├───README.md
├───server.py
├───start_server.bat
├───.idea\
├───out\
└───src\
    ├───CameraTest.class
    ├───CameraTest.java
    ├───gesture_model.tflite
    ├───hand_recognition_gui.py
    ├───model.tflite
    ├───server.py
    └───SignClient.java
```

- **`server.py`**: The Python backend server.
- **`src/SignClient.java`**: The Java Swing frontend client.
- **`gesture_model.tflite` / `model.tflite`**: The TensorFlow Lite model files.
- **`labels.txt`**: Contains the labels for the model's output.
- **`handsigns.db`**: The SQLite database file where predictions are stored (will be created on the first run).

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.