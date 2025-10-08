import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.videoio.VideoCapture;

public class CameraTest {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        // Open webcam (0 = default camera)
        VideoCapture camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            System.out.println("Error: Camera not detected!");
            return;
        }

        Mat frame = new Mat();

        while (true) {
            if (camera.read(frame)) {
                HighGui.imshow("Webcam - Press ESC to Exit", frame);

                // Exit on ESC key
                if (HighGui.waitKey(30) == 27) {
                    break;
                }
            } else {
                System.out.println("Error: Cannot read frame from camera.");
                break;
            }
        }

        camera.release();
        HighGui.destroyAllWindows();
    }
}
