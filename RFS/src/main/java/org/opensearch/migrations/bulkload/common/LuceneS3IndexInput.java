package org.opensearch.migrations.bulkload.common;

import org.apache.lucene.store.IndexInput;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.io.IOException;
import java.nio.ByteBuffer;

public class LuceneS3IndexInput extends IndexInput {

    private final S3Client s3Client;
    private final String bucketName;
    private final String objectKey;
    private Long length;
    private final long baseOffset;  // The base offset within the S3 object.
    private long position;  // The current position within this slice.


    public LuceneS3IndexInput(String resourceDescription, S3Client s3Client, String bucketName, String objectKey, long length) {
        this(resourceDescription, s3Client, bucketName, objectKey, length, 0);
    }

    private LuceneS3IndexInput(String resourceDescription, S3Client s3Client, String bucketName, String objectKey, long length, long baseOffset) {
        super(resourceDescription);
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.length = length;
        this.baseOffset = baseOffset;
        this.position = 0;
    }

    @Override
    public void close() throws IOException {
        // Nothing to close for S3 resources.
    }

    @Override
    public long getFilePointer() {
        return position;
    }

    @Override
    public void seek(long pos) throws IOException {
        if (pos < 0 || pos > length()) {
            throw new IOException("Position out of bounds: " + pos);
        }
        this.position = pos;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public byte readByte() throws IOException {
        byte[] buffer = new byte[1];
        readBytes(buffer, 0, 1);
        return buffer[0];
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
        if (position + len > length) {
            throw new IOException("Read exceeds file length");
        }

        long absolutePosition = baseOffset + position;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .range("bytes=" + absolutePosition + "-" + (absolutePosition + len - 1))
                .build();

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            ByteBuffer byteBuffer = objectBytes.asByteBuffer();
            byteBuffer.get(b, offset, len);
            position += len;
        } catch (S3Exception e) {
            throw new IOException("Failed to read bytes from S3: HTTP Status Code " + e.statusCode() + ", Reason: " + e.getMessage(), e);
        }
    }

    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > this.length) {
            throw new IOException("Slice is out of bounds: offset=" + offset + ", length=" + length + ", total length=" + this.length);
        }
        LuceneS3IndexInput slice = new LuceneS3IndexInput(getFullSliceDescription(sliceDescription), s3Client, bucketName, objectKey, length, baseOffset + offset);
        return slice;
    }

    @Override
    public IndexInput clone() {
        LuceneS3IndexInput clone = new LuceneS3IndexInput(this.toString(), s3Client, bucketName, objectKey, length, baseOffset);
        try {
            clone.seek(this.position);
        } catch (IOException e) {
            throw new AssertionError(e); // Should never happen.
        }
        return clone;
    }
}
