package meu.teste.upload;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class FileUpload extends HttpServlet {
    private static final long serialVersionUID = -8244073279641189889L;
    /**
     * Used below to determine the size of chucks to read in. Should be > 1kb and < 10MB
     */
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;

    /**
     * This is where backoff parameters are configured. Here it is aggressively retrying with
     * backoff, up to 10 times but taking no more that 15 seconds total to do so.
     */
    private final GcsService gcsService = GcsServiceFactory.createGcsService(new RetryParams.Builder()
            .initialRetryDelayMillis(10)
            .retryMaxAttempts(10)
            .totalRetryPeriodMillis(15000)
            .build());

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        oldMethod(req, res);
    }

    private GcsFilename getFileName(HttpServletRequest req) {
        String[] splits = req.getRequestURI().split("/", 4);
        if (!splits[0].equals("") || !splits[1].equals("gcs")) {
            throw new IllegalArgumentException("The URL is not formed as expected. " +
                    "Expecting /gcs/<bucket>/<object>");
        }
        return new GcsFilename(splits[2], splits[3]);
    }

    private void oldMethod(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        try {
            StringBuilder sb = new StringBuilder("{\"result\": [");

            if (req.getHeader("Content-Type") != null
                    && req.getHeader("Content-Type").startsWith("multipart/form-data")) {
                ServletFileUpload upload = new ServletFileUpload();

                FileItemIterator iterator = upload.getItemIterator(req);

                while (iterator.hasNext()) {
                    sb.append("{");
                    FileItemStream item = iterator.next();
                    sb.append("\"fieldName\":\"").append(item.getFieldName()).append("\",");
                    if (item.getName() != null) {
                        sb.append("\"name\":\"").append(item.getName()).append("\",");
                    }
                    if (item.getName() != null) {
                        sb.append("\"size\":\"").append(size(item.openStream())).append("\"");

                    } else {
                        sb.append("\"value\":\"").append(read(item.openStream())).append("\"");
                    }
                    sb.append("}");
                    if (iterator.hasNext()) {
                        sb.append(",");
                    }
                }
            } else {
                sb.append("{\"size\":\"" + size(req.getInputStream()) + "\"}");
            }

            sb.append("]");
            sb.append(", \"requestHeaders\": {");
            @SuppressWarnings("unchecked")
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                sb.append("\"").append(header).append("\":\"").append(req.getHeader(header)).append("\"");
                if (headerNames.hasMoreElements()) {
                    sb.append(",");
                }
            }
            sb.append("}}");

            res.getWriter().write(sb.toString());

        } catch (Exception ex) {
            throw new ServletException(ex);
        }

    }

    private void blob(HttpServletRequest req, HttpServletResponse res) throws IOException {
        BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();

        Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(req);

        Object blobKey = blobs.get("myFile");

        if (blobKey == null) {
            res.sendRedirect("/");
        } else {
            res.sendRedirect("/serve?blob-key=" + blobKey.toString());
        }
    }

    protected int size(InputStream stream) {
        int length = 0;
        try {
            byte[] buffer = new byte[2048];
            int size;
            while ((size = stream.read(buffer)) != -1) {
                length += size;
                /*for (int i = 0; i < size; i++) {
                    System.out.println(i+":"+size);
                }*/
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("returned " + length);
        return length;

    }

    protected String read(InputStream stream) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
            }
        }
        return sb.toString();

    }

    /**
     * Transfer the data from the inputStream to the outputStream. Then close both streams.
     */
    private void copy(InputStream input, OutputStream output) throws IOException {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = input.read(buffer);
            while (bytesRead != -1) {
                output.write(buffer, 0, bytesRead);
                bytesRead = input.read(buffer);
            }
        } finally {
            input.close();
            output.close();
        }
    }
}