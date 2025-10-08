import cv2
import mediapipe as mp
import time
import os
import tkinter as tk
from tkinter import font
from PIL import Image, ImageTk

# --- MediaPipe Gesture Recognizer Setup ---
mp_hands = mp.solutions.hands
GestureRecognizer = mp.tasks.vision.GestureRecognizer
GestureRecognizerOptions = mp.tasks.vision.GestureRecognizerOptions
VisionRunningMode = mp.tasks.vision.RunningMode

class HandRecognitionGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Hand Sign Recognition")

        # --- GUI Elements ---
        self.video_label = tk.Label(root)
        self.video_label.pack()

        self.prediction_label = tk.Label(root, text="Prediction: --", font=font.Font(size=24, weight='bold'))
        self.prediction_label.pack(pady=10)

        # --- Gesture Recognizer --- 
        model_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'model.tflite')
        options = GestureRecognizerOptions(
            base_options=mp.tasks.BaseOptions(model_asset_path=model_path),
            running_mode=VisionRunningMode.LIVE_STREAM,
            result_callback=self.save_result)
        self.recognizer = GestureRecognizer.create_from_options(options)

        # --- Webcam Setup ---
        self.cap = cv2.VideoCapture(0)
        if not self.cap.isOpened():
            print("FATAL: Could not open webcam.")
            return
        print("Webcam initialized.")

        self.latest_gesture = ""
        self.update()

    def save_result(self, result, output_image, timestamp_ms):
        if result.gestures:
            top_gesture = result.gestures[0][0]
            self.latest_gesture = top_gesture.category_name
        else:
            self.latest_gesture = "No hand detected"

    def update(self):
        success, frame = self.cap.read()
        if not success:
            self.root.after(10, self.update)
            return

        # Convert the BGR image to RGB and process it
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        self.recognizer.recognize_async(mp_image, int(time.time() * 1000))

        # --- Update Video Feed ---
        img = Image.fromarray(rgb_frame)
        imgtk = ImageTk.PhotoImage(image=img)
        self.video_label.imgtk = imgtk
        self.video_label.configure(image=imgtk)

        # --- Update Prediction Label ---
        self.prediction_label.config(text=f"Prediction: {self.latest_gesture}")

        self.root.after(10, self.update)

    def on_closing(self):
        self.recognizer.close()
        self.cap.release()
        self.root.destroy()

if __name__ == "__main__":
    root = tk.Tk()
    app = HandRecognitionGUI(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()