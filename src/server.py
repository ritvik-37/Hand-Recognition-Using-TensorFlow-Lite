import socket
import cv2
import mediapipe as mp
import numpy as np
import threading
import os

# --- Server Setup ---
HOST = '127.0.0.1'
PORT = 5000

# --- MediaPipe Gesture Recognizer Setup ---
GestureRecognizer = mp.tasks.vision.GestureRecognizer
GestureRecognizerOptions = mp.tasks.vision.GestureRecognizerOptions
VisionRunningMode = mp.tasks.vision.RunningMode

def handle_client(conn, addr):
    print(f"\nConnected by Java client {addr}")

    # Get the absolute path to the model file
    model_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'model.tflite')

    options = GestureRecognizerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=model_path),
        running_mode=VisionRunningMode.IMAGE)

    with GestureRecognizer.create_from_options(options) as recognizer:
        try:
            while True:
                # Read the length of the image data
                data_len_bytes = conn.recv(4)
                if not data_len_bytes:
                    print(f"\nJava client {addr} disconnected.")
                    break
                data_len = int.from_bytes(data_len_bytes, 'big')

                # Read the image data
                image_data = b''
                while len(image_data) < data_len:
                    packet = conn.recv(data_len - len(image_data))
                    if not packet:
                        break
                    image_data += packet

                if not image_data:
                    continue

                # Decode the image
                np_arr = np.frombuffer(image_data, np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

                if frame is None:
                    continue

                # Convert the BGR image to RGB
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

                # Recognize gestures in the image
                recognition_result = recognizer.recognize(mp_image)

                latest_gesture = "No hand detected"
                if recognition_result.gestures:
                    top_gesture = recognition_result.gestures[0][0]
                    latest_gesture = top_gesture.category_name

                # Send the prediction back to the client
                conn.sendall((latest_gesture + '\n').encode('utf-8'))

        except ConnectionResetError:
            print(f"\nJava client {addr} forcefully disconnected.")
        except Exception as e:
            print(f"\nClient connection error with {addr}: {e}")
        finally:
            conn.close()

def main():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((HOST, PORT))
        s.listen()
        print(f"--- Python Server Ready ---")
        print(f"Server listening on {HOST}:{PORT}. Waiting for a Java client to connect...")

        while True:
            conn, addr = s.accept()
            client_thread = threading.Thread(target=handle_client, args=(conn, addr))
            client_thread.start()

if __name__ == "__main__":
    main()
