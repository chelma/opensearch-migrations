package org.opensearch.migrations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.opensearch.migrations.bulkload.common.Uid;
import org.opensearch.migrations.bulkload.common.LuceneS3Directory;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;


import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class LuceneFromS3 {

    static DirectoryReader getReader(Path indexDirectoryPath) throws IOException {// Get the list of commits and pick the latest one
        try (FSDirectory directory = FSDirectory.open(indexDirectoryPath)) {
            List  <IndexCommit> commits = DirectoryReader.listCommits(directory);
            IndexCommit latestCommit = commits.get(commits.size() - 1);

            return DirectoryReader.open(
                latestCommit,
                6, // Minimum supported major version - Elastic 5/Lucene 6
                null // No specific sorting required
            );
        }
    }

    static DirectoryReader getS3Reader(S3Client s3Client, String bucketName, String indexPrefix) throws IOException {
        try (LuceneS3Directory directory = new LuceneS3Directory(s3Client, bucketName, indexPrefix)) {
            List  <IndexCommit> commits = DirectoryReader.listCommits(directory);
            IndexCommit latestCommit = commits.get(commits.size() - 1);

            return DirectoryReader.open(
                latestCommit,
                6, // Minimum supported major version - Elastic 5/Lucene 6
                null // No specific sorting required
            );
        }
    }


    public static void main(String[] args) throws Exception {
        // Print the current system time before the API call
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startTime = LocalDateTime.now().format(formatter);
        System.out.println("Start time: " + startTime);

        String bucketName = "chelma-iad-rfs-local-testing";
        String indexPrefix = "fwc_index_1/0/";

        // Specify the AWS region where your S3 bucket is located
        Region region = Region.US_EAST_1; // Change as appropriate

        // Create the S3 client with credentials from the default profile
        S3Client s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        DirectoryReader reader = getS3Reader(s3Client, bucketName, indexPrefix);
        log.info("Reading Lucene files from S3 bucket: {}", bucketName);

        // String indexDirectoryPath = "/tmp/lucene_files/fwc_index_1/0";
        // log.info("Reading Lucene files from {}", indexDirectoryPath);

        // DirectoryReader reader = getReader(Path.of(indexDirectoryPath));

        String readerTime = LocalDateTime.now().format(formatter);
        System.out.println("Reader setup time: " + readerTime);

        try ( SoftDeletesDirectoryReaderWrapper wrappedReader = new SoftDeletesDirectoryReaderWrapper(reader, "__soft_deletes")) {
            for (LeafReaderContext leafReaderContext : wrappedReader.leaves()) {
                var segmentReader = leafReaderContext.reader();
                var liveDocs = segmentReader.getLiveDocs();
    
                for (int docIdx = 0; docIdx < segmentReader.maxDoc(); docIdx++) {
                    if (liveDocs == null || liveDocs.get(docIdx)) {
                        Document document = segmentReader.storedFields().document(docIdx);
                        for (var field : document.getFields()) {
                            String fieldName = field.name();
                            switch (fieldName) {
                                case "_id": {
                                    var idBytes = field.binaryValue();
                                    log.info("Document ID: {}", Uid.decodeId(idBytes.bytes));
                                    break;
                                }
                                case "_source": {
                                    log.info("Document source: {}", field.binaryValue().utf8ToString());
                                    break;
                                }
                                default:
                                    break;
                            }
                        }
                    }
                }            
            }
        }
        
        
        String endTime = LocalDateTime.now().format(formatter);
        System.out.println("End time: " + endTime);
    }
    
}
