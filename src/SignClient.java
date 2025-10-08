import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgcodecs.Imgcodecs;

import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SignClient extends JFrame {

    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 5000;

    private VideoCapture camera;
    private volatile boolean running = false;
    private String currentPrediction = "...";
    private Thread recognitionThread;

    public SignClient() {
        setTitle("Hand Sign Recognition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel cameraPanel = new JLabel();
        cameraPanel.setHorizontalAlignment(SwingConstants.CENTER);
        cameraPanel.setBackground(Color.BLACK);
        cameraPanel.setOpaque(true);
        add(cameraPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel predictionLabel = new JLabel("Prediction: --", SwingConstants.CENTER);
        predictionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        bottomPanel.add(predictionLabel, BorderLayout.CENTER);

        JButton controlButton = new JButton("Start Recognition");
        controlButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        controlButton.addActionListener(e -> {
            if (!running) {
                startCameraAndPrediction(cameraPanel);
                controlButton.setText("Stop Recognition");
            } else {
                stopRecognition();
                dispose(); // Closes the window
            }
        });
        bottomPanel.add(controlButton, BorderLayout.WEST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Use a Swing Timer for periodic UI updates - avoids busy-waiting
        Timer predictionUpdater = new Timer(100, e -> predictionLabel.setText("Prediction: " + currentPrediction));
        predictionUpdater.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent ignoredEvent) {
                stopRecognition();
                predictionUpdater.stop();
            }
        });

        setVisible(true);
    }

    private void startCameraAndPrediction(JLabel cameraPanel) {
        camera = new VideoCapture(0, Videoio.CAP_DSHOW);
        if (!camera.isOpened()) {
            JOptionPane.showMessageDialog(this, "Error: Camera not detected!", "Camera Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        running = true;

        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);

        recognitionThread = new Thread(() -> {
            Mat frame = new Mat();
            long lastPredictionTime = 0;

            while (running) {
                if (camera.read(frame) && !frame.empty()) {
                    ImageIcon imageIcon = new ImageIcon(matToBufferedImage(frame));
                    SwingUtilities.invokeLater(() -> cameraPanel.setIcon(imageIcon));

                    // Send a frame for prediction every 200 ms
                    long now = System.currentTimeMillis();
                    if (now - lastPredictionTime > 200) {
                        lastPredictionTime = now;
                        MatOfByte matOfByte = new MatOfByte();
                        Imgcodecs.imencode(".jpg", frame, matOfByte);
                        currentPrediction = getPredictionFromServer(matOfByte.toArray());
                    }
                } else {
                    System.out.println("Failed to grab frame or frame is empty");
                }
            }
            camera.release();
        });
        recognitionThread.start();
    }

    private void stopRecognition() {
        running = false;
        if (recognitionThread != null) {
            try {
                recognitionThread.join(); // Wait for the thread to finish
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (camera != null) {
            camera.release();
        }
    }

    private String getPredictionFromServer(byte[] frameBytes) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             OutputStream out = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.write(ByteBuffer.allocate(4).putInt(frameBytes.length).array());
            out.write(frameBytes);
            String prediction = in.readLine();
            return (prediction != null && !prediction.isEmpty()) ? prediction.toUpperCase() : "...";
        } catch (IOException e) {
            return "SERVER DOWN";
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] b = new byte[mat.channels() * mat.cols() * mat.rows()];
        mat.get(0, 0, b);
        System.arraycopy(b, 0, ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, b.length);
        return image;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use JOptionPane for a more user-friendly error message than printStackTrace
            JOptionPane.showMessageDialog(null, "Failed to set the native look and feel.", "UI Error", JOptionPane.ERROR_MESSAGE);
        }
        SwingUtilities.invokeLater(SignClient::new);
    }
}