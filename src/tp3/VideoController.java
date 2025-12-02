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
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.scene.Node;

public class VideoController {

    private final ImageView viewLeft = new ImageView();
    private final ImageView viewRight = new ImageView();
    private final Label keyDisplay = new Label("Clé: N/A");

    private ScheduledExecutorService timer;
    private VideoCapture inputCap = new VideoCapture();
    private VideoWriter outputWriter = new VideoWriter();

    private MelangeDemelange cipher;

    private boolean isProcessing = false;
    private boolean isEncryptMode = true; // True pour Chiffrement, False pour Déchiffrement

    private String inputPath;
    private String outputPath;

    // Labels pour les titres des deux vidéos (mis à jour dans setup)
    private final Label titleLeft = new Label("Vidéo Gauche");
    private final Label titleRight = new Label("Vidéo Droite");

    // ---------------------------------------------------------
    //  UI
    // ---------------------------------------------------------
    public Node createRoot() {

        // Styles des ImageView
        viewLeft.setFitHeight(450);
        viewLeft.setFitWidth(550);
        viewLeft.setPreserveRatio(true);
        viewLeft.setSmooth(true);
        viewLeft.setStyle("-fx-border-color: #0077b6; -fx-border-width: 4; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 0);");

        viewRight.setFitHeight(450);
        viewRight.setFitWidth(550);
        viewRight.setPreserveRatio(true);
        viewRight.setSmooth(true);
        viewRight.setStyle("-fx-border-color: #0077b6; -fx-border-width: 4; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 0);");

        // Styles des titres et de la clé
        titleLeft.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #023e8a;");
        titleRight.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #023e8a;");
        keyDisplay.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        VBox boxLeft = new VBox(10, titleLeft, viewLeft);
        boxLeft.setAlignment(Pos.TOP_CENTER);
        VBox boxRight = new VBox(10, titleRight, viewRight);
        boxRight.setAlignment(Pos.TOP_CENTER);

        HBox videoBox = new HBox(30, boxLeft, boxRight);
        videoBox.setAlignment(Pos.CENTER);

        // Barre d'information pour la clé
        HBox infoBar = new HBox(20, keyDisplay);
        infoBar.setAlignment(Pos.CENTER);
        infoBar.setPadding(new Insets(10, 20, 10, 20));
        infoBar.setBackground(new Background(new BackgroundFill(Color.web("#0096c7"), new CornerRadii(10), Insets.EMPTY)));

        VBox root = new VBox(30, infoBar, videoBox);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setBackground(new Background(new BackgroundFill(Color.web("#F0F8FF"), CornerRadii.EMPTY, Insets.EMPTY)));

        return root;
    }

    // ---------------------------------------------------------
    //  SETUP & DÉMARRAGE
    // ---------------------------------------------------------
    public void setup(String inputPath, String outputPath, String key, String mode) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.cipher = new MelangeDemelange(key);
        this.keyDisplay.setText("Clé: " + key);

        if (mode.equalsIgnoreCase("CHIFFRER")) {
            isEncryptMode = true;
            titleLeft.setText("Vidéo d'origine (Non Chiffrée)");
            titleRight.setText("Vidéo Chiffrée (Mélangée)");
        } else if (mode.equalsIgnoreCase("DECHIFFRER")) {
            isEncryptMode = false;
            titleLeft.setText("Vidéo Chiffrée (Mélangée)");
            titleRight.setText("Vidéo Déchiffrée (Originale)");
        } else {
            System.err.println("Mode non reconnu. Utilisez 'CHIFFRER' ou 'DECHIFFRER'.");
            Platform.exit();
            return;
        }

        // Démarre le traitement immédiatement après le setup
        startProcess();
    }

    // ---------------------------------------------------------
    //  TRAITEMENT PRINCIPAL
    // ---------------------------------------------------------
    private void startProcess() {
        if (isProcessing) return;

        isProcessing = true;
        stopResources(); // Juste pour s'assurer que tout est propre

        if (!initializeVideoResources()) {
            System.err.println("Impossible d'initialiser les ressources vidéo.");
            isProcessing = false;
            return;
        }

        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(this::processFrame, 0, 33, TimeUnit.MILLISECONDS);
    }

    private boolean initializeVideoResources() {
        if (!inputCap.open(inputPath)) {
            System.err.println("Impossible d'ouvrir la vidéo d'entrée: " + inputPath);
            return false;
        }

        int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
        double fps = inputCap.get(Videoio.CAP_PROP_FPS);
        fps = (fps <= 0 ? 30 : fps);

        int width = (int) inputCap.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) inputCap.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        if (!outputWriter.open(outputPath, fourcc, fps, new Size(width, height), true)) {
            System.err.println("Impossible d'ouvrir le fichier de sortie: " + outputPath);
            return false;
        }

        return true;
    }

    private void processFrame() {
        Mat inputFrame = new Mat();
        inputCap.read(inputFrame);

        if (inputFrame.empty()) {
            // Fin de la vidéo, on boucle ou on arrête
            if (isProcessing) {
                inputCap.set(Videoio.CAP_PROP_POS_FRAMES, 0); // Boucle
                inputCap.read(inputFrame);
            } else {
                return; // Arrêt
            }
        }
        if (inputFrame.empty()) return;

        Mat outputFrame;

        // 1. Applique le chiffrement/déchiffrement
        if (isEncryptMode) {
            outputFrame = cipher.encrypt(inputFrame);
        } else {
            outputFrame = cipher.decrypt(inputFrame);
        }

        // 2. Enregistre le résultat du traitement
        outputWriter.write(outputFrame);

        // 3. Met à jour les vues pour l'affichage côte à côte
        Platform.runLater(() -> {
            if (isEncryptMode) {
                // Mode CHIFFRER: Gauche=Originale (inputFrame), Droite=Chiffrée (outputFrame)
                updateImageView(viewLeft, mat2Image(inputFrame));
                updateImageView(viewRight, mat2Image(outputFrame));
            } else {
                // Mode DECHIFFRER: Gauche=Chiffrée (inputFrame), Droite=Déchiffrée (outputFrame)
                updateImageView(viewLeft, mat2Image(inputFrame));
                updateImageView(viewRight, mat2Image(outputFrame));
            }
        });

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
    //  UTILITAIRES DE CONVERSION
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

        // Assurez-vous que le format de l'image est correct (BGR pour OpenCV couleur)
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