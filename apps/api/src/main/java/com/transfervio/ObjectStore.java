package com.transfervio;

import io.minio.*;
import java.io.*;
import org.springframework.stereotype.Service;

@Service
public class ObjectStore {
  final MinioClient minio;
  final RuntimeConfig config;

  ObjectStore(MinioClient minio, RuntimeConfig config) {
    this.minio = minio;
    this.config = config;
  }

  void put(String key, byte[] bytes, String type) {
    try {
      minio.putObject(
          PutObjectArgs.builder().bucket(config.bucket).object(key).contentType(type).stream(
                  new ByteArrayInputStream(bytes), bytes.length, -1)
              .build());
    } catch (Exception e) {
      throw Store.error(503, "Local object storage unavailable");
    }
  }

  byte[] get(String key) {
    try (var stream =
        minio.getObject(GetObjectArgs.builder().bucket(config.bucket).object(key).build())) {
      return stream.readNBytes(10 * 1024 * 1024 + 1);
    } catch (Exception e) {
      throw Store.error(503, "Local object unavailable");
    }
  }

  void delete(String key) {
    try {
      minio.removeObject(RemoveObjectArgs.builder().bucket(config.bucket).object(key).build());
    } catch (Exception e) {
      throw Store.error(503, "Local object cleanup failed; retry");
    }
  }

  java.util.List<String> expiredKeys(java.time.Instant cutoff) {
    var keys = new java.util.ArrayList<String>();
    try {
      for (var result :
          minio.listObjects(
              ListObjectsArgs.builder().bucket(config.bucket).recursive(true).build())) {
        var item = result.get();
        if (item.lastModified().toInstant().isBefore(cutoff)
            && item.objectName().matches("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}"))
          keys.add(item.objectName());
        if (keys.size() >= 100) break;
      }
    } catch (Exception e) {
      throw Store.error(503, "Retention scan unavailable");
    }
    return keys;
  }
}
