package Manager.Job;

import java.io.InputStream;

public interface DataStorageInterface {
    void createLibInfoFile(String libName, Object libInfo);
    int getFilesAmountInLib(String libName);
    String getLibUrl(String libName);
    String getLibOfFileFromUrl(String toString);
    InputStream getFile(String location);
    // returns true if finished
    boolean insertResult(String appMessageId, String input, String analysis, String output);
}
