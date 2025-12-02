package tp3;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Node;

public class VideoController {

    private final ImageView viewOriginal = new ImageView();
    private final ImageView viewProcessed = new ImageView();

    private final Button encryptButton = new Button("Chiffrer");
    private final Button decryptButton = new Button("Déchiffrer");
    private final Label keyDisplay = new Label("Clé: N/A");

    private ScheduledExecutorService timer;
    private VideoCapture inputCap = new VideoCapture();
    private VideoWriter outputWriter = new VideoWriter();

    private LineCipher cipher;

    private boolean isProcessing = false;
    private boolean modeEncrypt = true;

    private String inputPath;
    private String outputPath;
    private String mode;

    // ---------------------------------------------------------
    //  UI
    // ---------------------------------------------------------
    public Node createRoot() {

        viewOriginal.setFitHeight(600);
        viewOriginal.setFitWidth(550);
        viewOriginal.setPreserveRatio(true);
        viewProcessed.setFitHeight(600);
        viewProcessed.setFitWidth(550);
        viewProcessed.setPreserveRatio(true);

        encryptButton.setOnAction(e -> startProcess(true));
        decryptButton.setOnAction(e -> startProcess(false));

        Label titleOriginal = new Label("Vidéo Originale");
        titleOriginal.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label titleProcessed = new Label("Vidéo Traitée");
        titleProcessed.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox boxOriginal = new VBox(10, titleOriginal, viewOriginal);
        boxOriginal.setAlignment(Pos.CENTER);
        VBox boxProcessed = new VBox(10, titleProcessed, viewProcessed);
        boxProcessed.setAlignment(Pos.CENTER);

        HBox videoBox = new HBox(20, boxOriginal, boxProcessed);
        videoBox.setAlignment(Pos.CENTER);

        HBox buttonBar = new HBox(30, encryptButton, decryptButton, keyDisplay);
        buttonBar.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, videoBox, buttonBar);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #F5F5F5;");

        startPreview();

        return root;
    }

    // ---------------------------------------------------------
    //  SETUP
    // ---------------------------------------------------------
    public void setup(String inputPath, String outputPath, String key, String mode) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.mode = mode;
        this.cipher = new LineCipher(key);
        this.keyDisplay.setText("Clé: " + key);
    }

    // ---------------------------------------------------------
    //  PREVIEW
    // ---------------------------------------------------------
    private void startPreview() {
        stopResources();

        if (!inputCap.open(inputPath)) {
            System.err.println("Impossible d'ouvrir la vidéo (preview): " + inputPath);
            return;
        }

        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            Mat frame = new Mat();
            inputCap.read(frame);

            if (frame.empty()) {
                inputCap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
                return;
            }

            updateImageView(viewOriginal, mat2Image(frame));

        }, 0, 33, TimeUnit.MILLISECONDS);
    }

    // ---------------------------------------------------------
    //  TRAITEMENT
    // ---------------------------------------------------------
    private void startProcess(boolean encrypt) {
        if (isProcessing) return;

        isProcessing = true;
        modeEncrypt = encrypt;

        stopResources();

        if (!initializeVideoResources()) {
            System.err.println("Impossible d'initialiser la vidéo.");
            isProcessing = false;
            return;
        }

        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(this::processFrame, 0, 33, TimeUnit.MILLISECONDS);
    }

    private boolean initializeVideoResources() {
        if (!inputCap.open(inputPath)) {
            System.err.println("Impossible d'ouvrir la vidéo: " + inputPath);
            return false;
        }

        int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
        double fps = inputCap.get(Videoio.CAP_PROP_FPS);
        fps = (fps <= 0 ? 30 : fps);

        int width = (int) inputCap.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) inputCap.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        if (!outputWriter.open(outputPath, fourcc, fps, new Size(width, height), true)) {
            System.err.println("Impossible d'ouvrir la sortie: " + outputPath);
            return false;
        }

        return true;
    }

    private void processFrame() {
        Mat frame = new Mat();
        inputCap.read(frame);

        if (frame.empty()) {
            inputCap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
            inputCap.read(frame);
        }
        if (frame.empty()) return;

        Mat processed = modeEncrypt ? cipher.encrypt(frame) : cipher.decrypt(frame);

        outputWriter.write(processed);

        updateImageView(viewProcessed, mat2Image(processed));
    }

    // ---------------------------------------------------------
    //  STOP
    // ---------------------------------------------------------
    public void stopResources() {
        if (timer != null && !timer.isShutdown()) timer.shutdownNow();
        if (inputCap.isOpened()) inputCap.release();
        if (outputWriter.isOpened()) outputWriter.release();
        isProcessing = false;
    }

    // ---------------------------------------------------------
    //  UTILITAIRES
    // ---------------------------------------------------------
    private void updateImageView(ImageView view, Image image) {
        onFXThread(view.imageProperty(), image);
    }

    public static Image mat2Image(Mat frame) {
        try {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        } catch (Exception e) {
            System.err.println("Conversion Mat -> Image échouée: " + e);
            return null;
        }
    }

    private static BufferedImage matToBufferedImage(Mat original) {
        int width = original.width();
        int height = original.height();
        int channels = original.channels();

        if (!original.isContinuous()) original = original.clone();

        byte[] source = new byte[width * height * channels];
        original.get(0, 0, source);

        BufferedImage image = (channels > 1)
                ? new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
                : new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(source, 0, target, 0, source.length);

        return image;
    }

    public static <T> void onFXThread(ObjectProperty<T> property, T value) {
        Platform.runLater(() -> property.set(value));
    }
}
