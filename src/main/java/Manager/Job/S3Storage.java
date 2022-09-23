package Manager.Job;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import javax.json.*;
import javax.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ListIterator;

public class S3Storage implements DataStorageInterface {
    private String bucketName;
    private S3Client s3;
    private static final String idFileName= "ID-INFO.json";

    public S3Storage(String bucketName, S3Client s3) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    @Override
    public InputStream getFile(String location) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(this.bucketName).key(location).build();
        return this.s3.getObject(getObjectRequest);
    }

    @Override
    public boolean insertResult(String appMessageId, String input, String analysis, String output) {
        boolean finished = true;
        InputStream idFile = this.getFile(appMessageId + "/" + idFileName);
        JsonReader reader = Json.createReader(idFile);
        JsonObject fileJsonRep = reader.readObject();
        JsonArray oldFiles = fileJsonRep.getJsonArray("files");
        JsonArrayBuilder newFiles = Json.createArrayBuilder();
        for (JsonValue file:
             oldFiles) {
            if (file instanceof JsonObject){
                if (((JsonObject) file).get("inputLink") instanceof JsonString
                    && ((JsonString)((JsonObject) file).get("inputLink")).getString().equals(input)
                    && ((JsonObject) file).get("analysisType") instanceof JsonString
                    && ((JsonString)((JsonObject) file).get("analysisType")).getString().equals(analysis)){
                    newFiles.add(Json.createObjectBuilder()
                            .add("inputLink", input)
                            .add("analysisType", analysis)
                            .add("output", output).build());
                    continue;
                }
            }
            newFiles.add(file);
        }
        // Check if we have all the results
        JsonArray finishedNewFiles = newFiles.build();
        for (JsonValue file:
                finishedNewFiles) {
            if (file instanceof JsonObject) {
                if (((JsonObject) file).get("output") instanceof JsonString
                    && ((JsonString)((JsonObject) file).get("output")).getString().equals("")){
                    finished = false;
                    break;
                }
            }
        }
        this.createLibInfoFile(appMessageId, Json.createObjectBuilder().add("files", finishedNewFiles).build());
        return finished;
    }

    @Override
    public void createLibInfoFile(String libName, Object libInfo) {
        if (libInfo instanceof JsonObject) {
            PutObjectRequest putObjectRequest = PutObjectRequest
                    .builder()
                    .bucket(this.bucketName)
                    .key(libName + "/" + idFileName)
                    .build();
            s3.putObject(putObjectRequest,
                    RequestBody.fromBytes(libInfo.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public int getFilesAmountInLib(String libName) {
        try {
            int sum = 0;
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(libName).build();
            ListObjectsV2Iterable response = this.s3.listObjectsV2Paginator(request);
            for (ListObjectsV2Response page : response) {
                sum += page.contents().size();
            }
            return sum;

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return 0;
    }

    @Override
    public String getLibUrl(String libName) {
        return "s3//:" + this.bucketName + "/" + libName;
    }

    @Override
    public String getLibOfFileFromUrl(String url) {
        String[] strings = url.split("/");
        return strings[strings.length-2];
    }
}
