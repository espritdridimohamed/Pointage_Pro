package com.pointagepro.shared.approval.repository;

import com.pointagepro.shared.approval.entity.Approval;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByRequestTypeAndRequestIdOrderByStepOrderAsc(String requestType, Long requestId);

    Optional<Approval> findFirstByRequestTypeAndRequestIdAndStatusCode(String requestType, Long requestId, String statusCode);

    Optional<Approval> findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(String requestType, Long requestId, String statusCode);

    List<Approval> findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(String requestType, Long requestId, String statusCode);

    /**
     * Entity-graph variant eagerly loading the status and approver needed to map a step to its
     * response DTO after the transaction commits.
     */
    @EntityGraph(attributePaths = {"status", "approver"})
    @Query("""
            select a from Approval a
            where a.requestType = :requestType and a.requestId = :requestId
            order by a.stepOrder asc""")
    List<Approval> findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
            @Param("requestType") String requestType, @Param("requestId") Long requestId);

    @EntityGraph(attributePaths = {"status", "approver"})
    @Query("""
            select a from Approval a
            where a.requestType = :requestType and a.requestId in :requestIds
            order by a.stepOrder asc""")
    List<Approval> findWithDetailsByRequestTypeAndRequestIdInOrderByStepOrderAsc(
            @Param("requestType") String requestType, @Param("requestIds") List<Long> requestIds);
}
