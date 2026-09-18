package com.vivo4redes.syscor.mailing.repository;

import com.vivo4redes.syscor.mailing.model.CampanhaMailing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampanhaMailingRepository extends JpaRepository<CampanhaMailing, Long> {
    List<CampanhaMailing> findAllByOrderByCriadoEmDesc();
}