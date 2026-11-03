package com.vivo4redes.syscor.estoque.repository;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
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

    // 1. Verificações e buscas básicas por serial
    boolean existsBySerial(String serial);

    List<EstoqueGeral> findBySerialIn(List<String> seriais);

    Optional<EstoqueGeral> findBySerialAndStatus(String serial, StatusEstoque status);

    List<EstoqueGeral> findByStatus(StatusEstoque status);

    List<EstoqueGeral> findBySerialInAndStatusNot(List<String> seriais, StatusEstoque status);

    // 2. Buscas por modelo
    List<EstoqueGeral> findByModeloComercialInAndStatus(List<String> modelos, StatusEstoque status);

    /**
     * Itens do modelo cujo status é DIFERENTE do informado (ex.: tudo que não é VENDIDO).
     * As chaves de modelo chegam em maiúsculas e sem espaços (ConversorValores.chaveModelo),
     * então a comparação normaliza o valor do banco do mesmo jeito.
     */
    @Query("SELECT e FROM EstoqueGeral e WHERE UPPER(TRIM(e.modeloComercial)) IN :modelos AND e.status <> :status")
    List<EstoqueGeral> findByModeloIn(@Param("modelos") List<String> modelos, @Param("status") StatusEstoque status);

    @Query("SELECT DISTINCT UPPER(TRIM(e.modeloComercial)) FROM EstoqueGeral e WHERE UPPER(TRIM(e.modeloComercial)) IN :modelos")
    List<String> findModelosExistentes(@Param("modelos") List<String> modelos);

    // 3. Bloqueios pessimistas para concorrência (PDV / Baixas)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.id = :id")
    Optional<EstoqueGeral> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial")
    Optional<EstoqueGeral> findBySerialForUpdate(@Param("serial") String serial);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial AND e.status = :status")
    Optional<EstoqueGeral> findBySerialAndStatusForUpdate(@Param("serial") String serial, @Param("status") StatusEstoque status);

    // 4. Busca de disponíveis

    /**
     * Busca do PDV: unidades no status informado, filtradas por filial (opcional) e termo livre.
     * <ul>
     *   <li>{@code serial}: o termo já normalizado como serial (sem espaços, maiúsculas — ConversorValores.serial),
     *       comparado por trecho com {@code e.serial}; permite IMEI completo ou parcial.</li>
     *   <li>{@code termo}: o texto digitado (só com trim), comparado sem diferenciar maiúsculas com
     *       modelo, marca e código de acessório — preserva espaços de nomes como "Galaxy A15".</li>
     * </ul>
     * Vazio em {@code serial}/{@code termo} ou {@code filial} desliga o respectivo filtro.
     */
    @Query("""
        SELECT e FROM EstoqueGeral e
        WHERE e.status = :status
          AND (:filial = '' OR UPPER(TRIM(e.filialEstoque)) = UPPER(TRIM(:filial)))
          AND ((:serial = '' AND :termo = '')
               OR (:serial <> '' AND e.serial LIKE CONCAT('%', :serial, '%'))
               OR (:termo <> '' AND (
                      LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :termo, '%'))
                   OR LOWER(e.marca) LIKE LOWER(CONCAT('%', :termo, '%'))
                   OR LOWER(e.codigoAcessorio) LIKE LOWER(CONCAT('%', :termo, '%')))))
        ORDER BY e.modeloComercial, e.serial
        """)
    List<EstoqueGeral> buscarDisponiveisPorFiltro(@Param("status") StatusEstoque status,
                                                  @Param("serial") String serial,
                                                  @Param("termo") String termo,
                                                  @Param("filial") String filial,
                                                  Pageable pageable);

    // 5. Consolidação
    @Query("""
    SELECT NEW com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO(
        e.modeloComercial,
        MAX(e.marca),
        e.categoria,
        SUM(CASE WHEN e.status = 'DISPONIVEL' THEN 1 ELSE 0 END),
        SUM(CASE WHEN e.status = 'RESERVADO' THEN 1 ELSE 0 END),
        SUM(CASE WHEN e.status = 'AVARIADO' THEN 1 ELSE 0 END),
        MIN(e.valorVenda))
    FROM EstoqueGeral e
    GROUP BY e.modeloComercial, e.categoria
    """)
    List<EstoqueConsolidadoModeloDTO> consolidarPorModelo();
}