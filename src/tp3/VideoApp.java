package tp3;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VideoApp extends Application {

    private VideoController controller;

    @Override
    public void start(Stage primaryStage) {
        controller = new VideoController();

        // Récupération des arguments de ligne de commande
        Parameters params = getParameters();
        String[] args = params.getRaw().toArray(new String[0]);

        // Vérification du nombre d'arguments
        if (args.length < 4) {
            System.err.println("Erreur: arguments manquants.");
            System.err.println("Usage: java -jar app.jar <MODE> <inputVideo> <outputVideo> <key_or_keyFile>");
            System.err.println("Exemple 1 (Clé directe): java -jar app.jar CHIFFRER /chemin/video_claire.avi /chemin/video_chiffree.avi ma_super_cle");
            System.err.println("Exemple 2 (Clé par fichier): java -jar app.jar DECHIFFRER /chemin/video_chiffree.avi /chemin/video_dechiffree.avi /chemin/vers/ma_cle.txt");
            Platform.exit();
            return;
        }

        // Convertir l'argument du mode en majuscules pour garantir la cohérence
        String rawMode = args[0].toUpperCase();
        String inputPath = args[1];
        String outputPath = args[2];
        String keySource = args[3]; // Peut être la clé elle-même ou le chemin du fichier

        String key = "";

        // --- LOGIQUE DE GESTION DE LA CLÉ (DIRECTE OU FICHIER) ---
        // 1. Vérifie si l'argument est un chemin de fichier valide
        if (Files.exists(Paths.get(keySource)) && !Files.isDirectory(Paths.get(keySource))) {
            try {
                // Lit le contenu du fichier et utilise la première ligne comme clé
                key = Files.readAllLines(Paths.get(keySource)).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .findFirst()
                        .orElseThrow(() -> new IOException("Le fichier de clé est vide ou ne contient pas de clé valide."));
                System.out.println("Clé lue à partir du fichier: " + keySource);
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture du fichier de clé " + keySource + ": " + e.getMessage());
                Platform.exit();
                return;
            }
        } else {
            // 2. Utilise l'argument comme clé directe
            key = keySource;
        }

        // --- HARMONISATION DU MODE ---
        String mode;
        if (rawMode.equals("ENCRYPT") || rawMode.equals("CHIFFRER")) {
            mode = "CHIFFRER";
        } else if (rawMode.equals("DECRYPT") || rawMode.equals("DECHIFFRER")) {
            mode = "DECHIFFRER";
        } else {
            System.err.println("Erreur: Mode '" + args[0] + "' non reconnu.");
            System.err.println("Utilisez 'CHIFFRER' (ou 'ENCRYPT') ou 'DECHIFFRER' (ou 'DECRYPT').");
            Platform.exit();
            return;
        }

        // L'UI est créée, puis le processus est démarré dans le setup du contrôleur
        Scene scene = new Scene((Parent) controller.createRoot(), 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Visualisation et Traitement Vidéo - Mode: " + mode);
        primaryStage.show();

        // Initialisation du contrôleur et démarrage du processus vidéo
        controller.setup(inputPath, outputPath, key, mode);
    }

    @Override
    public void stop() {
        // S'assurer que les ressources OpenCV et le timer sont libérés à la fermeture de l'application
        if (controller != null) {
            controller.stopResources();
        }
    }

    public static void main(String[] args) {
        // Assurez-vous que la librairie native OpenCV est chargée avant de lancer l'application
        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Erreur: La librairie native OpenCV (" + org.opencv.core.Core.NATIVE_LIBRARY_NAME + ") n'a pas pu être trouvée.");
            System.err.println("Veuillez vous assurer que le chemin vers la librairie est correctement configuré.");
            return;
        }
        launch(args);
    }
}