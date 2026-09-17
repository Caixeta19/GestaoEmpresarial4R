package com.vivo4redes.syscor.gestao.repository;

import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProtocoloDocumentalRepository extends JpaRepository<ProtocoloDocumental, Long> {
    Optional<ProtocoloDocumental> findByVendaId(Long vendaId);

    List<ProtocoloDocumental> findByNumeroProtocoloGed(String numeroProtocoloGed);
}