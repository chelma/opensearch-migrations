package org.opensearch.migrations.bulkload.common;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

public class LuceneS3Directory extends Directory {

    private final S3Client s3Client;
    private final String bucketName;
    private final String indexPrefix;
    private String[] allFiles;

    public LuceneS3Directory(S3Client s3Client, String bucketName, String indexPrefix) {
        super();
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.indexPrefix = indexPrefix.endsWith("/") ? indexPrefix : indexPrefix + "/";
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public long fileLength(String name) throws IOException {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(indexPrefix + name)
                    .build();
            HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);
            return headObjectResponse.contentLength();
        } catch (Exception e) {
            throw new IOException("Failed to get file length for " + name + " from S3", e);
        }
    }

    @Override
    public String[] listAll() throws IOException {
        if (allFiles != null) {
            return allFiles;
        }

        try {
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(indexPrefix)
                    .build();
            ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);
            allFiles = listObjectsResponse.contents().stream()
                    .map(S3Object::key)
                    .map(key -> key.substring(indexPrefix.length()))
                    .toArray(String[]::new);

            return allFiles;
        } catch (Exception e) {
            throw new IOException("Failed to list objects in S3 bucket for prefix " + indexPrefix, e);
        }
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
        long length = fileLength(name);

        return new LuceneS3IndexInput(name, s3Client, bucketName, indexPrefix + name, length);
    }

    /*
     * The following methods are not implemented as this class is read-only.
     */

    @Override
    public IndexOutput createOutput(String name, IOContext context) {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public void deleteFile(String name) {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public Set<String> getPendingDeletions() throws IOException {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public Lock obtainLock(String name) throws IOException {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public void rename(String source, String dest) {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public void sync(Collection<String> names) {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }

    @Override
    public void syncMetaData() {
        throw new UnsupportedOperationException("LuceneS3Directory is read-only");
    }
}
