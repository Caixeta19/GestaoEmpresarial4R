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
     * Busca do PDV: termo livre (serial, modelo, marca, código de acessório) e filial opcional.
     * Nome próprio de propósito: com 4 parâmetros String/String, a sobrecarga abaixo
     * (status, categoria, termo, pageable) era escolhida por engano e trocava os argumentos.
     */
    @Query("""
        SELECT e FROM EstoqueGeral e
        WHERE e.status = :status
          AND (:filial = '' OR UPPER(TRIM(e.filialEstoque)) = UPPER(TRIM(:filial)))
          AND (:termo = ''
               OR e.serial LIKE CONCAT('%', :termo, '%')
               OR LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :termo, '%'))
               OR LOWER(e.marca) LIKE LOWER(CONCAT('%', :termo, '%'))
               OR LOWER(e.codigoAcessorio) LIKE LOWER(CONCAT('%', :termo, '%')))
        ORDER BY e.modeloComercial, e.serial
        """)
    List<EstoqueGeral> buscarDisponiveisPorFiltro(@Param("status") StatusEstoque status,
                                                  @Param("termo") String termo,
                                                  @Param("filial") String filial,
                                                  Pageable pageable);

    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status AND (:termo IS NULL OR :termo = '' OR e.serial LIKE %:termo% OR e.modeloComercial LIKE %:termo%)")
    List<EstoqueGeral> buscarDisponiveis(@Param("status") StatusEstoque status, @Param("termo") String termo, Pageable pageable);

    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status AND (:categoria IS NULL OR :categoria = '' OR e.categoria = :categoria) AND (:termo IS NULL OR :termo = '' OR e.serial LIKE %:termo% OR e.modeloComercial LIKE %:termo%)")
    List<EstoqueGeral> buscarDisponiveis(@Param("status") StatusEstoque status, @Param("categoria") String categoria, @Param("termo") String termo, Pageable pageable);

    // Métodos de apoio para aparelhos e acessórios
    default List<EstoqueGeral> buscarAparelhosDisponiveis(StatusEstoque status, String termo, Pageable pageable) {
        return buscarDisponiveis(status, "PRODUTO_VIVO", termo, pageable);
    }

    default List<EstoqueGeral> buscarAcessoriosDisponiveis(StatusEstoque status, String termo, Pageable pageable) {
        return buscarDisponiveis(status, "ACESSORIO", termo, pageable);
    }

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