package android.projecttest1601;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_features2d;
import org.bytedeco.javacpp.opencv_nonfree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.bytedeco.javacpp.opencv_highgui.imread;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // defined at each class creation
    static String tag = MainActivity.class.getName();

    //Components of this screen
    Button btnCapture;
    Button btnAnalysis;
    Button btnLibrary;
    ImageView imageCaptured;

    // Request Code of the Capture activity
    static int Capture_RequestCode = 1;
    // Request Code of the Library activity
    static int Library_RequestCode = 2;

    // SIFT keypoint features
    private static final int N_FEATURES = 0;
    private static final int N_OCTAVE_LAYERS = 3;
    private static final double CONTRAST_THRESHOLD = 0.04;
    private static final double EDGE_THRESHOLD = 10;
    private static final double SIGMA = 1.6;


    public opencv_core.Mat img;
    private opencv_nonfree.SIFT SiftDesc;
    private opencv_core.Mat descriptor;

    private String filePath;
    private opencv_core.Mat[] descriptorsRef;
    private File bestFileMatching;
    private File file_analysis;
    private opencv_core.Mat[] images_ref;

    private Uri mImageUri;
    private String result;


    public static File ToCache(Context context, String Path, String fileName) {
        InputStream input;
        FileOutputStream output;
        byte[] buffer;
        String filePath = context.getCacheDir() + "/" + fileName;
        File file = new File(filePath);
        AssetManager assetManager = context.getAssets();

        try {
            input = assetManager.open(Path);
            buffer = new byte[input.available()];
            input.read(buffer);
            input.close();

            output = new FileOutputStream(filePath);
            output.write(buffer);
            output.close();
            return file;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCapture = (Button) findViewById(R.id.btnCapture);
        btnAnalysis = (Button) findViewById(R.id.btnAnalysis);
        btnLibrary = (Button) findViewById(R.id.btnLibrary);
        imageCaptured = (ImageView) findViewById(R.id.imageCaptured);

        btnCapture.setOnClickListener(this);
        btnLibrary.setOnClickListener(this);
        btnAnalysis.setOnClickListener(this);
        imageCaptured.setOnClickListener(this);

        SiftDesc = new opencv_nonfree.SIFT(N_FEATURES, N_OCTAVE_LAYERS, CONTRAST_THRESHOLD, EDGE_THRESHOLD, SIGMA);



        //Get all references images
        images_ref = new opencv_core.Mat[]{};
        try {
             /*
             **   get Ref Images with the path
             **   SIFT+compute
             **   Returns Array of Images Ref
             */

            images_ref = handling_ImagesRef("Data_BOW/test");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            //Click on Capture Button
            case R.id.btnCapture:
                Intent mediaCapture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(mediaCapture, Capture_RequestCode);
                break;
            //Click on Library Button
            case R.id.btnLibrary:
                Intent mediaLibrary = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(mediaLibrary, Library_RequestCode);
                break;
            //Click on Analysis Button
            case R.id.btnAnalysis:
                filePath = mImageUri.getPath();
                Log.i(tag, "absolutepath "+filePath);

                try {
                    result = analyse();
                    Log.i(tag,"result = "+result);

                    //Display the matched file
                    //Bitmap myBitmap = BitmapFactory.decodeFile("app/assets/Data_BOW/test/"+result);
                    //imageCaptured.setImageBitmap(myBitmap);

                    // get input stream
                    InputStream ims = getAssets().open("Data_BOW/test/"+result);
                    // load image as Drawable
                    Drawable d = Drawable.createFromStream(ims, null);
                    // set image to ImageView
                    imageCaptured.setImageDrawable(d);
                    ims .close();


                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
            case R.id.imageCaptured:
                //Ouvrir navigateur web avec l'url de l'image matchée
                //String fileName = bestFileMatching.getName();
                String fileName = result;
                fileName = fileName.substring(0,fileName.lastIndexOf('_'));
                String url = "https://fr.wikipedia.org/wiki/"+fileName;
                Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
                startActivity(intent);
            default:
                break;
        }
    }

    protected String analyse() throws IOException {
        //String refFile = "Coca_13.jpg";
        //this.filePath = this.ToCache(this, path + "/" + refFile, refFile).getPath();
        //Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        img = imread(this.filePath);
        descriptor = new opencv_core.Mat();
        opencv_features2d.KeyPoint keypoints = new opencv_features2d.KeyPoint();
        SiftDesc.detect(img, keypoints);
        SiftDesc.compute(img, keypoints, descriptor);

        Log.i("test", "Nb of detected keypoints:" + keypoints.capacity());

        opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher();
        opencv_features2d.DMatchVectorVector[] matches = new opencv_features2d.DMatchVectorVector[images_ref.length];
        opencv_features2d.DMatchVectorVector[] bestMatches = new opencv_features2d.DMatchVectorVector[images_ref.length];
        float minDistance = Float.MAX_VALUE;
        int imageRefNumber = 0;

        for(int i=0;i<images_ref.length;i++){
            matches[i] = new opencv_features2d.DMatchVectorVector();
            matcher.knnMatch(descriptor, descriptorsRef[i], matches[i], 2);
            float distanceCurrent = refineMatches(matches[i]);

            Log.i(tag, "score "+distanceCurrent);
            if(distanceCurrent<minDistance){
                minDistance = distanceCurrent;
                imageRefNumber = i;
            }
            Log.i(tag, "min "+minDistance);

        }

        AssetManager assetManager = getAssets();
        String[] allPaths = assetManager.list("Data_BOW/test");

        return allPaths[imageRefNumber];

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if image is captured by the

        if((requestCode == Capture_RequestCode || requestCode == Library_RequestCode) && resultCode == RESULT_OK){

            mImageUri = data.getData();
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            //imageCaptured.setImageURI(selectedImageUri);
            imageCaptured.setImageBitmap(imageBitmap);

        }
    }

    public static opencv_core.Mat load(File file, int flags) throws IOException {
        opencv_core.Mat image;
        if(!file.exists()) {
            throw new FileNotFoundException("Image file does not exist: " + file.getAbsolutePath());
        }
        image = imread(file.getAbsolutePath(),flags);
        if(image == null || image.empty()) {
            throw new IOException("Couldn't load image: " + file.getAbsolutePath());
        }
        return image;
    }

    /**
     +     *
     +     * @param path: path Images of reference
     +     * @param nFeatures
     +     * @param nOctaveLayers
     +     * @param contrastThreshold
     +     * @param edgeThreshold
     +     * @param sigma
     +     * @return : Images references Array
     +     * @throws IOException
     +     */
    public opencv_core.Mat[] handling_ImagesRef(String path)throws IOException{
        AssetManager assetManager = getAssets();
        String[] allPaths = assetManager.list(path);


        opencv_core.Mat[] imagesRef = new opencv_core.Mat[allPaths.length];
        opencv_features2d.KeyPoint[] keyPointsRef = new opencv_features2d.KeyPoint[allPaths.length];
        opencv_nonfree.SIFT siftRef = new opencv_nonfree.SIFT(N_FEATURES, N_OCTAVE_LAYERS, CONTRAST_THRESHOLD, EDGE_THRESHOLD, SIGMA);
        descriptorsRef = new opencv_core.Mat[allPaths.length];

        for(int i=0;i<allPaths.length;i++){
            File file = this.ToCache(this,path+File.separator+allPaths[i], allPaths[i] );
            //Load image
            imagesRef[i] = load(file, 1);

            //Create KeyPoints
            keyPointsRef[i] = new opencv_features2d.KeyPoint();

            //detect SURF features and compute descriptors for both images
            siftRef.detect(imagesRef[0],keyPointsRef[0]);
            //Create CvMat initialized with empty pointer, using simply 'new Mat()' leads to an exception
            descriptorsRef[i] = new opencv_core.Mat();
            siftRef.compute(imagesRef[i], keyPointsRef[i], descriptorsRef[i]);

        }
        System.out.println("Nb fichiers Ref : " + imagesRef.length);
        return imagesRef;
    }


    private static float refineMatches(opencv_features2d.DMatchVectorVector oldMatches) {
        // Ratio of Distances
        double RoD = 0.6;
        opencv_features2d.DMatchVectorVector newMatches = new opencv_features2d.DMatchVectorVector();

        // Refine results 1: Accept only those matches, where best dist is < RoD
        // of 2nd best match.
        int sz = 0;
        newMatches.resize(oldMatches.size());

        double maxDist = 0.0, minDist = 1e100; // infinity

        for (int i = 0; i < oldMatches.size(); i++) {
            newMatches.resize(i, 1);
            if (oldMatches.get(i, 0).distance() < RoD
                    * oldMatches.get(i, 1).distance()) {
                newMatches.put(sz, 0, oldMatches.get(i, 0));
                sz++;
                double distance = oldMatches.get(i, 0).distance();
                if (distance < minDist)
                    minDist = distance;
                if (distance > maxDist)
                    maxDist = distance;
                //Log.i(tag,"DISTANNNNNNNNCE "+ distance);
            }
        }
        newMatches.resize(sz);

        // Refine results 2: accept only those matches which distance is no more
        // than 3x greater than best match
        sz = 0;
        opencv_features2d.DMatchVectorVector brandNewMatches = new opencv_features2d.DMatchVectorVector();
        brandNewMatches.resize(newMatches.size());
        for (int i = 0; i < newMatches.size(); i++) {
            // TODO: Move this weights into params: Move this weights into params
            // Since minDist may be equal to 0.0, add some non-zero value
            if (newMatches.get(i, 0).distance() <= 3 * minDist) {
                brandNewMatches.resize(sz, 1);
                brandNewMatches.put(sz, 0, newMatches.get(i, 0));
                sz++;
            }
        }
        brandNewMatches.resize(sz);

        float somme= 0;
        for(int i=0;i<brandNewMatches.size();i++){
            somme += brandNewMatches.get(i,0).distance();
        }
        return somme / brandNewMatches.size();
    }


}
