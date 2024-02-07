package imageanalysis;

// Java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// OpenCV
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.android.Utils;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

// Android
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

// Android Vision
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OCR
{
    private static TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private static List<String> master_list = new ArrayList<>();

    public static List<Text.Element> read_cards(Bitmap bitmap_image,
                                                AssetManager asset_manager,
                                                int number_expected,
                                                List<Text.Element> first_word_list)
    {
        if (master_list.isEmpty())
            master_list = get_master_list(asset_manager);

        if (number_expected < 1)
            number_expected = 1;

        Mat mat_image = new Mat();
        Utils.bitmapToMat(bitmap_image, mat_image);

        Imgproc.cvtColor(mat_image, mat_image, Imgproc.COLOR_BGR2GRAY);

        Core.rotate(mat_image, mat_image, Core.ROTATE_180);

        isolate_characters(mat_image);

        Utils.matToBitmap(mat_image, bitmap_image);

        List<Text.Element> word_list = extract_codenames_words(bitmap_image, first_word_list);

        for (double threshold = 0.25; word_list.size() < number_expected && threshold <= 1.0; threshold += 0.25)
        {
            Mat mat_clone = mat_image.clone();
            fill_holes(mat_clone, threshold);
            Utils.matToBitmap(mat_clone, bitmap_image);
            word_list = merge_word_lists(word_list,
                    extract_codenames_words(bitmap_image, first_word_list));
        }

        for (double threshold = 0.1; word_list.size() < number_expected && threshold < 1.0; threshold += 0.1)
        {
            if (threshold != 0.5)
            {
                Mat mat_clone = mat_image.clone();
                fill_holes(mat_clone, threshold);
                Utils.matToBitmap(mat_clone, bitmap_image);
                word_list = merge_word_lists(word_list,
                        extract_codenames_words(bitmap_image, first_word_list));
            }
        }

        return word_list;
    }

    private static void isolate_characters(Mat frame)
    {
        Core.normalize(frame, frame, 0, 255, Core.NORM_MINMAX);
        Imgproc.adaptiveThreshold(frame, frame, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 51, 50);
    }

    private static void fill_holes(Mat frame, double threshold)
    {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(frame, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);

        List<MatOfPoint> fill_contours = new ArrayList<>();
        double[] areas = new double[contours.size()];
        double avg_area = 0.0;

        for (int i = 0; i < contours.size(); i++)
        {
            areas[i] = Imgproc.contourArea(contours.get(i));
            avg_area += areas[i];
        }

        avg_area /= contours.size();

        for (int i = 0; i < contours.size(); i++)
            if (areas[i] < avg_area * threshold | areas[i] > avg_area * 50)
                fill_contours.add(contours.get(i));

        Scalar color = new Scalar(0);

        Imgproc.drawContours(frame, fill_contours, -1, color, Imgproc.FILLED);
    }

    private static List<Text.Element> merge_word_lists(List<Text.Element> list1, List<Text.Element> list2)
    {
        List<Text.Element> merged_list = new ArrayList<>(list1);

        for (Text.Element word2 : list2)
        {
            boolean found = false;

            for (Text.Element word1 : list1)
            {
                if (word1.getText().equals(word2.getText()))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
                merged_list.add(word2);
        }

        return merged_list;
    }

    private static List<Text.Element> extract_codenames_words(Bitmap bitmap_image,
                                                              List<Text.Element> first_word_list)
    {
        InputImage image = InputImage.fromBitmap(bitmap_image, 0);

        List<Text.Element> word_list = new ArrayList<>();
        List<Text.Element> denoised_word_list = new ArrayList<>();

        Task<Text> result = recognizer.process(image);

        // Stalls until task is complete
        while(!result.isComplete()) {}

        for (Text.TextBlock block : result.getResult().getTextBlocks())
            for (Text.Line line : block.getLines())
                word_list.addAll(line.getElements());

        for (int i = 0; i < word_list.size(); i++)
            if (get_exact_match(word_list.get(i).getText(), first_word_list))
                denoised_word_list.add(word_list.get(i));

        return denoised_word_list;
    }

    private static boolean get_exact_match(String str, List<Text.Element> first_word_list)
    {
        for (Text.Element element : first_word_list)
            if (str.equals(element.getText()))
                return true;

        for (String line : master_list)
            if (str.equals(line))
                return true;

        return false;
    }

    private static List<String> get_master_list(AssetManager asset_manager)
    {
        BufferedReader reader;
        List<String> master_list = new ArrayList<>();
        try {
            reader = new BufferedReader(new InputStreamReader(asset_manager.open("CodenamesWordList.txt")));
            while (reader.ready())
                master_list.add(reader.readLine());
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return master_list;
    }
}
