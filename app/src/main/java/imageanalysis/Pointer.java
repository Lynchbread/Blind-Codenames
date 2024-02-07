package imageanalysis;

import static org.opencv.core.Core.inRange;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.getStructuringElement;

import com.google.mlkit.vision.text.Text;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class Pointer
{
    public static double get_distance(Mat mat_image, Text.Element word, int[] original_resolution)
    {
        double distance = 0.0;

        Core.rotate(mat_image, mat_image, Core.ROTATE_180);

        Mat gray_mat = extract_green(mat_image);

        Imgproc.erode(gray_mat, gray_mat, getStructuringElement(Imgproc.MORPH_RECT, new Size(4, 4)));

        int row = word.getBoundingBox().centerY() * gray_mat.rows() / original_resolution[0];
        int col = word.getBoundingBox().centerX() * gray_mat.cols() / original_resolution[1];

        // Just in case a pixel is somehow out of bounds.
        if (row >= gray_mat.rows())
            row = gray_mat.rows() - 1;

        if (col >= gray_mat.cols())
            col = gray_mat.cols() - 1;

        int pixel = (int)(gray_mat.get(row, col)[0]);

        // if not over card
        if (pixel == 0)
        {
            Mat labels = new Mat();
            Mat stats = new Mat();
            Mat centroids = new Mat();
            Imgproc.connectedComponentsWithStats(gray_mat, labels, stats, centroids);

            int number_of_components = stats.rows();

            if (number_of_components <= 1)
            {
                distance = 1.0;
            }
            else
            {
                double shortest_distance = 1.0;

                Point center_of_word = new Point((double)col / (double)gray_mat.cols(),
                        (double)row / (double)gray_mat.rows());

                for (int i = 1; i < number_of_components; i++)
                {
                    Point center_of_pointer = new Point(centroids.get(i, 0)[0] / (double)gray_mat.cols(),
                            centroids.get(i, 1)[0] / (double)gray_mat.rows());

                    distance = calculate_distance(center_of_pointer, center_of_word);

                    if (distance < shortest_distance)
                        shortest_distance = distance;
                }

                distance = shortest_distance;
            }

            labels.release();
            stats.release();
            centroids.release();
        }

        gray_mat.release();

        return distance;
    }

    private static double calculate_distance(Point p1, Point p2)
    {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    private static Mat extract_green(Mat mat_image)
    {
        Mat gray_mat = new Mat();
        cvtColor(mat_image, gray_mat, Imgproc.COLOR_BGR2HSV);

        inRange(gray_mat, new Scalar (36.0, 25.0, 25.0), new Scalar (70.0, 255.0,255.0), gray_mat);

        return gray_mat;
    }
}
