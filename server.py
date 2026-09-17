import os
import socket
import sqlite3
import threading
from datetime import datetime

import cv2
import mediapipe as mp
import numpy as np
from flask import Flask, jsonify, request

# --- Server Configuration ---
HOST = '127.0.0.1'
PORT = 5000          # TCP socket the Java client connects to
HTTP_PORT = 5001     # Flask endpoint that serves prediction history

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, 'gesture_recognizer.task')
DB_PATH = os.path.join(BASE_DIR, 'handsigns.db')

# Sentinel the client shows when no hand is in frame. Never logged.
NO_HAND = "No hand detected"

# --- MediaPipe Gesture Recognizer Setup ---
GestureRecognizer = mp.tasks.vision.GestureRecognizer
GestureRecognizerOptions = mp.tasks.vision.GestureRecognizerOptions
VisionRunningMode = mp.tasks.vision.RunningMode

# One recognizer is shared by every client thread: loading the model costs ~8 MB
# of disk reads, so it happens once at startup rather than per connection.
recognizer = None
recognizer_lock = threading.Lock()

# sqlite3 connections are not thread-safe, so the single connection shared by the
# client threads and the Flask thread is guarded by a lock.
db = None
db_lock = threading.Lock()


def init_db():
    """Open handsigns.db, creating the predictions table if it is not there yet."""
    connection = sqlite3.connect(DB_PATH, check_same_thread=False)
    connection.execute("""
        CREATE TABLE IF NOT EXISTS predictions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            label TEXT NOT NULL,
            timestamp TEXT NOT NULL
        )
    """)
    connection.commit()
    return connection


def log_prediction(label):
    """Record a recognized gesture. Callers filter out the no-hand sentinel."""
    with db_lock:
        db.execute(
            "INSERT INTO predictions (label, timestamp) VALUES (?, ?)",
            (label, datetime.now().isoformat(timespec='seconds')),
        )
        db.commit()


def recognize_gesture(frame):
    """Return the top gesture name for a BGR frame, or the no-hand sentinel."""
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

    with recognizer_lock:
        recognition_result = recognizer.recognize(mp_image)

    if recognition_result.gestures:
        return recognition_result.gestures[0][0].category_name
    return NO_HAND


def recv_exactly(conn, count):
    """Read exactly count bytes, or return None if the client hangs up first."""
    buffer = b''
    while len(buffer) < count:
        packet = conn.recv(count - len(buffer))
        if not packet:
            return None
        buffer += packet
    return buffer


def handle_client(conn, addr):
    print(f"\nConnected by Java client {addr}")
    last_logged = None

    try:
        while True:
            # --- Receive image length ---
            data_len_bytes = recv_exactly(conn, 4)
            if data_len_bytes is None:
                print(f"\nJava client {addr} disconnected.")
                break
            data_len = int.from_bytes(data_len_bytes, 'big')

            # --- Receive image data ---
            image_data = recv_exactly(conn, data_len)
            if image_data is None:
                print(f"\nJava client {addr} disconnected mid-frame.")
                break

            # --- Decode and recognize ---
            # A single bad frame should cost one prediction, not the connection.
            try:
                np_arr = np.frombuffer(image_data, np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
                if frame is None:
                    continue
                latest_gesture = recognize_gesture(frame)
            except Exception as e:
                print(f"\nFrame from {addr} could not be processed: {e}")
                continue

            # Log only when the gesture changes: the client sends 5 frames a
            # second, so logging every one would bury the table in duplicates.
            if latest_gesture != NO_HAND and latest_gesture != last_logged:
                log_prediction(latest_gesture)
            last_logged = latest_gesture

            # --- Send result back ---
            conn.sendall((latest_gesture + '\n').encode('utf-8'))

    except ConnectionResetError:
        print(f"\nJava client {addr} forcefully disconnected.")
    except Exception as e:
        print(f"\nClient connection error with {addr}: {e}")
    finally:
        conn.close()


# --- Prediction History API ---
app = Flask(__name__)


@app.route('/history')
def history():
    """Most recent predictions, newest first. Optional ?limit=N (default 50)."""
    try:
        limit = min(max(int(request.args.get('limit', 50)), 1), 1000)
    except ValueError:
        return jsonify({"error": "limit must be an integer"}), 400

    with db_lock:
        rows = db.execute(
            "SELECT id, label, timestamp FROM predictions ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()

    return jsonify([
        {"id": row[0], "label": row[1], "timestamp": row[2]} for row in rows
    ])


def serve_http():
    # Local-only: this is a desktop tool, it has no business on other interfaces.
    app.run(host=HOST, port=HTTP_PORT, threaded=True)


def main():
    global recognizer, db

    if not os.path.exists(MODEL_PATH):
        raise SystemExit(f"Model not found at {MODEL_PATH}")

    db = init_db()

    options = GestureRecognizerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=MODEL_PATH),
        running_mode=VisionRunningMode.IMAGE
    )

    with GestureRecognizer.create_from_options(options) as shared_recognizer:
        recognizer = shared_recognizer

        threading.Thread(target=serve_http, daemon=True).start()

        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind((HOST, PORT))
            s.listen()
            print("--- Python Gesture Server Ready ---")
            print(f"Model loaded from {MODEL_PATH}")
            print(f"Listening on {HOST}:{PORT}. Waiting for Java clients...")
            print(f"Prediction history: http://{HOST}:{HTTP_PORT}/history")

            while True:
                conn, addr = s.accept()
                threading.Thread(
                    target=handle_client, args=(conn, addr), daemon=True
                ).start()


if __name__ == "__main__":
    main()
