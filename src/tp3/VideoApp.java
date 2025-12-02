package tp3;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoApp extends Application {

    private VideoController controller;

    @Override
    public void start(Stage primaryStage) {
        controller = new VideoController();

        // Récupération des arguments de ligne de commande
        Parameters params = getParameters();
        String[] args = params.getRaw().toArray(new String[0]);
        if (args.length < 4) {
            System.err.println("Usage: java -jar app.jar <MODE> <inputVideo> <outputVideo> <key>");
            Platform.exit();
            return;
        }

        String mode = args[0].toUpperCase();
        String inputPath = args[1];
        String outputPath = args[2];
        String key = args[3];

        controller.setup(inputPath, outputPath, key, mode);

        Scene scene = new Scene((Parent) controller.createRoot(), 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Visualisation et Chiffrement Vidéo");
        primaryStage.show();
    }

    @Override
    public void stop() {
        controller.stopResources();
    }

    public static void main(String[] args) {
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME); // charge OpenCV
        launch(args);
    }
}
