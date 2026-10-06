package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusWebhook;
import com.vivo4redes.syscor.financeiro.model.WebhookLog;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    boolean existsByProviderAndPayloadSha256AndStatusNot(String provider, String sha, StatusWebhook status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from WebhookLog l where l.id = :id")
    Optional<WebhookLog> findByIdForUpdate(@Param("id") Long id);

    @Query("select l from WebhookLog l where l.status in :status and l.tentativas < :max and l.recebidoEm < :ate order by l.recebidoEm")
    List<WebhookLog> findPendentes(@Param("status") Collection<StatusWebhook> status, @Param("max") int max,
                                   @Param("ate") OffsetDateTime ate, Pageable page);

    List<WebhookLog> findAllByOrderByRecebidoEmDesc(Pageable page);
}