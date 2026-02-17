package com.demo.devops.auditservice.repository;

import com.demo.devops.auditservice.domain.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  @Query(value = "SELECT * FROM audit_events ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
  List<AuditEvent> findRecent(@Param("limit") int limit);
}
