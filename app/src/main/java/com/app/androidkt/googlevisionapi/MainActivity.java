package com.app.androidkt.googlevisionapi;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.ColorInfo;
import com.google.api.services.vision.v1.model.DominantColorsAnnotation;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.ImageProperties;
import com.google.api.services.vision.v1.model.SafeSearchAnnotation;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.filter.Filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int RECORD_REQUEST_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 102;
    private static final int REQUEST_CODE_CHOOSE =103;
    private static final int PERMISSION_REQUEST_CODE = 1;
    String[] PERMISSIONS = {"android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE"};

    private static final String CLOUD_VISION_API_KEY = "AIzaSyDYeXKKaqev24bVMqH9tGfI5pjO7gdb-Fc";

    @BindView(R.id.takePicture)
    Button takePicture;

    @BindView(R.id.selectPicture)
    Button selectPicture;

    @BindView(R.id.imageProgress)
    ProgressBar imageUploadProgress;

    @BindView(R.id.imageView)
    ImageView imageView;

    @BindView(R.id.spinnerVisionAPI)
    Spinner spinnerVisionAPI;

    @BindView(R.id.visionAPIData)
    TextView visionAPIData;
    private Feature feature;
    private Bitmap bitmap;

    private int cnt;
    public List<Bitmap>bitmaps;
    private String url = "http://52.78.159.170:3000/upload/photos";

    public List<Uri> mSelected;

    //private String[] visionAPI = new String[]{"LANDMARK_DETECTION", "LOGO_DETECTION", "SAFE_SEARCH_DETECTION", "IMAGE_PROPERTIES", "LABEL_DETECTION"};
    private String[] visionAPI = new String[]{"LABEL_DETECTION"};

    private String api = visionAPI[0];

    private void requestNecessaryPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mSelected = new ArrayList<>();


        feature = new Feature();
        feature.setType(visionAPI[0]);
        feature.setMaxResults(10);

        spinnerVisionAPI.setOnItemSelectedListener(this);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, visionAPI);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVisionAPI.setAdapter(dataAdapter);
        spinnerVisionAPI.setVisibility(View.INVISIBLE);


        selectPicture.setOnClickListener(new View.OnClickListener(){ //사진고르기

            @Override
            public void onClick(View view) {
                requestNecessaryPermissions(PERMISSIONS);
                Matisse.from(MainActivity.this)
                        .choose(MimeType.ofAll())
                        .countable(true)
                        .maxSelectable(9)
                        .addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                        .gridExpectedSize(getResources().getDimensionPixelSize(R.dimen.grid_expected_size))
                        .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                        .thumbnailScale(0.85f)
                        .imageEngine(new MyGlideEngine())
                        .forResult(REQUEST_CODE_CHOOSE);
            }
        });


        takePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePictureFromCamera();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture.setVisibility(View.VISIBLE);
        } else {
            takePicture.setVisibility(View.INVISIBLE);
            makeRequest(Manifest.permission.CAMERA);
        }
    }

    private int checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission);
    }

    private void makeRequest(String permission) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, RECORD_REQUEST_CODE);
    }

    public void takePictureFromCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        //비트맵리스트를 만든다
        bitmaps = new ArrayList<>();

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data"); //사진을 받아온다
            imageView.setImageBitmap(bitmap);//사진을 보여준다.

            //1개의 비트맵으로 비트맵리스트에 넣어준다
            //비트맵리스트를 만든다
            bitmaps.add(bitmap);
            SendHttpRequestTask t = new SendHttpRequestTask();
            t.execute(url);
            callCloudVision(bitmaps, feature);

            cnt = 1;
        }
        else if(requestCode == REQUEST_CODE_CHOOSE && resultCode == RESULT_OK)
        {
            mSelected = Matisse.obtainResult(data);
            Log.d("Matisse","mSelected: "+ mSelected);
            for(int i = 0 ; i < mSelected.size() ;i++) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mSelected.get(i));
                    bitmaps.add(bitmap);
                    Log.d("superdroid","bitmap transformed Success!!");
                }catch (Exception e){
                    Log.d("superdroid","FaILED!");
                    e.printStackTrace();
                }
            }
            cnt = bitmaps.size();
            Log.d("superdroid",""+bitmaps.size());
           /* for(int i = 0 ; i < selected_photos.size(); i++){
              Log.d("superdroid","selected : "+ selected_photos.get(i).toString());
            }*/
// 여기서 이제 gcp 를 실행시켜야지. 비트맵 리스트를 넘겨줘야지.
            SendHttpRequestTask t = new SendHttpRequestTask();
            t.execute(url);
            callCloudVision(bitmaps, feature);
        }
    }


    private class SendHttpRequestTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String url = params[0];
            try {
                HttpClient client = new HttpClient(url);
                client.connectForMultipart();
                for(int i=0; i<bitmaps.size(); i++) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    //ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Bitmap tempBitmap = bitmaps.get(i);
                    //Bitmap b = BitmapFactory.decodeResource(MainActivity.this.getResources(), R.drawable.logo);
                    tempBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    //b.compress(Bitmap.CompressFormat.PNG, 0, baos);
                    byte[] imageBytes = baos.toByteArray();
                    client.addFilePart("photo", "photo.jpeg", imageBytes);
                }
                client.finishMultipart();
                String data = client.getResponse();
            }
            catch(Throwable t) {
                t.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            Log.i("superdroid", "onPostExecute: success");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == RECORD_REQUEST_CODE) {
            if (grantResults.length == 0 && grantResults[0] == PackageManager.PERMISSION_DENIED
                    && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish();
            } else {
                takePicture.setVisibility(View.VISIBLE);
            }
        }
    }

    private void callCloudVision(final List<Bitmap> bitmaps, final Feature feature) {
        imageUploadProgress.setVisibility(View.VISIBLE);
        final List<Feature> featureList = new ArrayList<>();
        featureList.add(feature);

        final List<AnnotateImageRequest> annotateImageRequests = new ArrayList<>();
     /*   //카메라에서 찍힌사진
        AnnotateImageRequest annotateImageReq = new AnnotateImageRequest();
        annotateImageReq.setFeatures(featureList);
        annotateImageReq.setImage(getImageEncodeImage(bitmap)); //bitmap:카메라에서 찍은사진
        annotateImageRequests.add(annotateImageReq);
        //카메라가 아닌, selected 된 사진들로 리퀘스트
        Log.d("seolhee","phase1");
        Bitmap bitmap1 = BitmapFactory.decodeResource(getResources(), R.drawable.us);
        annotateImageReq.setImage(getImageEncodeImage(bitmap1)); //set as us.jpg
        annotateImageRequests.add(annotateImageReq);

*/

     for(int i =0 ; i<1; i++){
         AnnotateImageRequest annotateImageReq = new AnnotateImageRequest();
         annotateImageReq.setFeatures(featureList);
         annotateImageReq.setImage(getImageEncodeImage(bitmaps.get(i))); //bitmap:카메라에서 찍은사진
         annotateImageRequests.add(annotateImageReq);
     }

/*
        AnnotateImageRequest annotateImageReq2 = new AnnotateImageRequest();
        annotateImageReq2.setFeatures(featureList);
        annotateImageReq2.setImage(getImageEncodeImage(bitmap)); //bitmap:카메라에서 찍은사진
        annotateImageRequests.add(annotateImageReq2);*/

        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {

                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    VisionRequestInitializer requestInitializer = new VisionRequestInitializer(CLOUD_VISION_API_KEY);

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(requestInitializer);

                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(annotateImageRequests); //List를 받는다.

                    Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
                    annotateRequest.setDisableGZipContent(true);
                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    //출력해보기1.
                    Log.d("superdroid","response"+response);
                    return convertResponseToString(response); //이게 스트링이며, 구글의 RESPONSE 가있다..
                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                visionAPIData.setText(result);
                imageUploadProgress.setVisibility(View.INVISIBLE);

                Intent myIntent = new Intent(MainActivity.this,PostActivity.class);
                myIntent.putExtra("JSON_STR",result);
                startActivity(myIntent);

            }
        }.execute();
    }

    @NonNull
    private Image getImageEncodeImage(Bitmap bitmap) {
        Image base64EncodedImage = new Image();
        // Convert the bitmap to a JPEG
        // Just in case it's a format that Android understands but Cloud Vision
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        // Base64 encode the JPEG
        base64EncodedImage.encodeContent(imageBytes);
        return base64EncodedImage;
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {

        AnnotateImageResponse imageResponses = response.getResponses().get(0);

        List<EntityAnnotation> entityAnnotations;

        String message = "";
        switch (api) {
            case "LANDMARK_DETECTION":
                entityAnnotations = imageResponses.getLandmarkAnnotations();

                message = formatAnnotation(entityAnnotations);
                break;
            case "LOGO_DETECTION":
                entityAnnotations = imageResponses.getLogoAnnotations();
                message = formatAnnotation(entityAnnotations);
                break;
            case "SAFE_SEARCH_DETECTION":
                SafeSearchAnnotation annotation = imageResponses.getSafeSearchAnnotation();
                message = getImageAnnotation(annotation);
                break;
            case "IMAGE_PROPERTIES":
                ImageProperties imageProperties = imageResponses.getImagePropertiesAnnotation();
                message = getImageProperty(imageProperties);
                break;
            case "LABEL_DETECTION":
                entityAnnotations = imageResponses.getLabelAnnotations();
                //출력해보기
                Log.d("superdroid","msg 전의 entityannotations"+ entityAnnotations.toString());

                message = formatAnnotation(entityAnnotations);
                break;
        }
        return message;
    }

    private String getImageAnnotation(SafeSearchAnnotation annotation) {
        return String.format("adult: %s\nmedical: %s\nspoofed: %s\nviolence: %s\n",
                annotation.getAdult(),
                annotation.getMedical(),
                annotation.getSpoof(),
                annotation.getViolence());
    }

    private String getImageProperty(ImageProperties imageProperties) {
        String message = "";
        DominantColorsAnnotation colors = imageProperties.getDominantColors();
        for (ColorInfo color : colors.getColors()) {
            message = message + "" + color.getPixelFraction() + " - " + color.getColor().getRed() + " - " + color.getColor().getGreen() + " - " + color.getColor().getBlue();
            message = message + "\n";
        }
        return message;
    }

    private String formatAnnotation(List<EntityAnnotation> entityAnnotation) {
        Gson gson = new Gson();
        JsonObject msgobject = new JsonObject();
        String json;

        if (entityAnnotation != null) {
            for (EntityAnnotation entity : entityAnnotation) {
                msgobject.addProperty(entity.getDescription(), entity.getScore());
            }
            msgobject.addProperty("cnt", cnt);
            json = gson.toJson(msgobject);
        } else {
            json = null;
        }
        return json;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        api = (String) adapterView.getItemAtPosition(i);
        feature.setType(api);
        if (bitmap != null){
            Log.d("seolhee","if you see this message ,there could be bugs");
        }
            //callCloudVision(bitmaps, feature);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
