package com.vivo4redes.syscor.estoque.dto.request;

import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** US-201: cadastro de produtos/itens de estoque, com SKU único. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoRequestDTO {

    @NotBlank(message = "SKU é obrigatório")
    private String sku;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Categoria é obrigatória")
    private CategoriaItemVenda categoria;

    @Min(value = 0, message = "Estoque mínimo não pode ser negativo")
    private Integer estoqueMinimo;

    @NotNull(message = "Preço base é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "Preço base deve ser maior que zero")
    private BigDecimal precoBase;

    /** Define se a baixa deste produto exige leitura de IMEI/serial (ex.: aparelhos). */
    private Boolean requerSerial;
}