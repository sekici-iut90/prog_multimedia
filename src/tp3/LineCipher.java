package tp3;

import java.util.Random;
import org.opencv.core.Mat;

/**
 * Gère le chiffrement et le déchiffrement des trames vidéo par mélange de lignes.
 */
public class LineCipher {

    private final long seed;

    public LineCipher(String key) {
        // Utilise le hash de la clé comme graine pour garantir une permutation reproductible.
        // On convertit en long pour Random(long).
        this.seed = (long) key.hashCode();
    }

    /**
     * Calcule la permutation des indices de lignes (clé de chiffrement).
     */
    private int[] generatePermutation(int rows) {
        int[] indices = new int[rows];
        for (int i = 0; i < rows; i++) {
            indices[i] = i;
        }

        Random random = new Random(seed);
        // Algorithme de mélange de Fisher-Yates
        for (int i = rows - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = indices[i];
            indices[i] = indices[j];
            indices[j] = temp;
        }
        return indices;
    }

    /**
     * Chiffre la trame : applique la permutation pour mélanger les lignes.
     */
    public Mat encrypt(Mat originalFrame) {
        if (originalFrame == null || originalFrame.empty()) return originalFrame;

        int rows = originalFrame.rows();
        int cols = originalFrame.cols();
        int type = originalFrame.type();

        int[] permutation = generatePermutation(rows);
        Mat encryptedMat = new Mat(rows, cols, type);

        // La ligne 'i' de l'original va à la position 'permutation[i]' dans la chiffrée.
        for (int i = 0; i < rows; i++) {
            Mat originalRow = originalFrame.row(i);
            Mat targetRow = encryptedMat.row(permutation[i]);
            originalRow.copyTo(targetRow);
        }

        return encryptedMat;
    }

    /**
     * Déchiffre la trame : applique la permutation INVERSE pour rétablir l'ordre.
     */
    public Mat decrypt(Mat encryptedFrame) {
        if (encryptedFrame == null || encryptedFrame.empty()) return encryptedFrame;

        int rows = encryptedFrame.rows();
        int cols = encryptedFrame.cols();
        int type = encryptedFrame.type();

        int[] permutation = generatePermutation(rows);
        Mat decryptedMat = new Mat(rows, cols, type);

        // La ligne à la position 'permutation[i]' dans la chiffrée va à la position 'i' dans la déchiffrée.
        for (int i = 0; i < rows; i++) {
            Mat encryptedRow = encryptedFrame.row(permutation[i]);
            Mat targetRow = decryptedMat.row(i);
            encryptedRow.copyTo(targetRow);
        }

        return decryptedMat;
    }
}
