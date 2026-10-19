package com.transfervio;

import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {
  final Store store;
  final MigrationService migrations;

  JobWorker(Store store, MigrationService migrations) {
    this.store = store;
    this.migrations = migrations;
  }

  @Scheduled(fixedDelay = 1500, initialDelay = 3000)
  public void work() {
    var jobs =
        store.rows(
            "update jobs set status='RUNNING',attempts=attempts+1,lease_until=now()+interval '30"
                + " minutes' where id=(select id from jobs where kind='EXTRACT' and"
                + " ((status='PENDING' and available_at<=now()) or (status='RUNNING' and"
                + " lease_until<now())) and attempts<3 order by created_at for update skip locked"
                + " limit 1) returning *");
    if (jobs.isEmpty()) return;
    var job = jobs.getFirst();
    try {
      migrations.extract(Store.id(job.get("migration_id")));
      store.db.update(
          "update jobs set status='DONE',lease_until=null,error_code=null,error_message=null where"
              + " id=?",
          job.get("id"));
    } catch (Exception e) {
      boolean terminal =
          ((Number) job.get("attempts")).intValue() >= 3
              || (e instanceof AiFailure ai && !ai.retryable);
      String code = e instanceof AiFailure ai ? ai.code : "EXTRACTION_FAILED";
      String message =
          e instanceof AiFailure ai
              ? ai.getReason()
              : "Extraction failed; check the source file and review its mapping";
      store.db.update(
          "update jobs set"
              + " status=?,lease_until=null,error_code=?,error_message=?,available_at=now()+interval"
              + " '30 seconds' where id=?",
          terminal ? "FAILED" : "PENDING",
          code,
          message,
          job.get("id"));
      if (terminal)
        store.db.update(
            "update migrations set status='FAILED' where id=? and status='PROCESSING'",
            job.get("migration_id"));
    }
  }
}
