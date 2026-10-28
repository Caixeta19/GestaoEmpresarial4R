package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstoqueGeralRepository extends JpaRepository<EstoqueGeral, Long> {

    // 1. Verificar existência por serial
    boolean existsBySerial(String serial);

    // 2. Buscar múltiplos seriais de uma vez
    List<EstoqueGeral> findBySerialIn(List<String> seriais);

    // 3. Buscar por modelo e status
    List<EstoqueGeral> findByModeloComercialInAndStatus(List<String> modelos, StatusEstoque status);

    // Alias caso o serviço utilize nome alternativo para modelo
    default List<EstoqueGeral> findByModeloIn(List<String> modelos, StatusEstoque status) {
        return findByModeloComercialInAndStatus(modelos, status);
    }

    // 4. Buscar seriais excluindo determinado status
    List<EstoqueGeral> findBySerialInAndStatusNot(List<String> seriais, StatusEstoque status);

    // 5. Encontrar modelos existentes na base
    @Query("SELECT DISTINCT e.modeloComercial FROM EstoqueGeral e WHERE e.modeloComercial IN :modelos")
    List<String> findModelosExistentes(@Param("modelos") List<String> modelos);

    // 6. Bloqueio pessimista para concorrência no PDV por serial
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial")
    Optional<EstoqueGeral> findBySerialForUpdate(@Param("serial") String serial);

    // 7. Buscar com bloqueio pessimista por serial e status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial AND e.status = :status")
    Optional<EstoqueGeral> findBySerialAndStatusForUpdate(@Param("serial") String serial, @Param("status") StatusEstoque status);

    // 8. Buscar padrão por serial e status simples
    Optional<EstoqueGeral> findBySerialAndStatus(String serial, StatusEstoque status);

    // 9. Buscar disponíveis com filtro genérico (modelo ou serial)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status AND (:termo IS NULL OR :termo = '' OR e.serial LIKE %:termo% OR e.modeloComercial LIKE %:termo%)")
    List<EstoqueGeral> buscarDisponiveis(@Param("status") StatusEstoque status, @Param("termo") String termo, Pageable pageable);

    // Métodos específicos para aparelhos vs acessórios se aplicável
    default List<EstoqueGeral> buscarAparelhosDisponiveis(StatusEstoque status, String termo, Pageable pageable) {
        return buscarDisponiveis(status, termo, pageable);
    }

    default List<EstoqueGeral> buscarAcessoriosDisponiveis(StatusEstoque status, String termo, Pageable pageable) {
        return buscarDisponiveis(status, termo, pageable);
    }

    // 10. Consulta auxiliar para consolidação por modelo (caso utilize projeção ou listagem agrupada)
    @Query("SELECT e FROM EstoqueGeral e")
    List<EstoqueGeral> consolidarPorModelo();
}