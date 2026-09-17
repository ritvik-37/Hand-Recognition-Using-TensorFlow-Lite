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
    private static final int SOCKET_TIMEOUT_MS = 3000;

    private VideoCapture camera;
    private volatile boolean running = false;
    private volatile String currentPrediction = "...";
    private Thread recognitionThread;

    // One connection is kept open for the whole session. Reconnecting per frame
    // would churn through a socket five times a second.
    private Socket socket;
    private OutputStream socketOut;
    private BufferedReader socketIn;

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
                // Shut down off the EDT so a blocked socket read cannot freeze the UI.
                controlButton.setEnabled(false);
                new Thread(() -> {
                    stopRecognition();
                    SwingUtilities.invokeLater(this::dispose);
                }, "shutdown").start();
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
        }, "recognition");
        recognitionThread.start();
    }

    private void stopRecognition() {
        running = false;
        Thread worker = recognitionThread;
        recognitionThread = null;
        if (worker != null) {
            try {
                // Bounded wait: the worker only ever blocks for the socket timeout.
                worker.join(SOCKET_TIMEOUT_MS + 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeConnection();
        if (camera != null) {
            camera.release();
        }
    }

    /** Opens the session socket if it is not already connected. */
    private void ensureConnected() throws IOException {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            return;
        }
        closeConnection();
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        socketOut = socket.getOutputStream();
        socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Nothing useful to do while tearing down.
        } finally {
            socket = null;
            socketOut = null;
            socketIn = null;
        }
    }

    private String getPredictionFromServer(byte[] frameBytes) {
        try {
            ensureConnected();
            socketOut.write(ByteBuffer.allocate(4).putInt(frameBytes.length).array());
            socketOut.write(frameBytes);
            socketOut.flush();
            String prediction = socketIn.readLine();
            if (prediction == null) {
                // Server closed the stream - drop the socket so the next frame reconnects.
                closeConnection();
                return "SERVER DOWN";
            }
            return prediction.isEmpty() ? "..." : prediction.toUpperCase();
        } catch (IOException e) {
            closeConnection();
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
