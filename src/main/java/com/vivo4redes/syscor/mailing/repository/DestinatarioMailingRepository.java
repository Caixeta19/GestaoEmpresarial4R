package com.vivo4redes.syscor.mailing.repository;

import com.vivo4redes.syscor.mailing.enums.StatusEnvio;
import com.vivo4redes.syscor.mailing.model.DestinatarioMailing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DestinatarioMailingRepository extends JpaRepository<DestinatarioMailing, Long> {
    List<DestinatarioMailing> findByCampanhaIdOrderByIdAsc(Long campanhaId);

    List<DestinatarioMailing> findByCampanhaIdAndStatus(Long campanhaId, StatusEnvio status);

    long countByCampanhaIdAndStatus(Long campanhaId, StatusEnvio status);
}