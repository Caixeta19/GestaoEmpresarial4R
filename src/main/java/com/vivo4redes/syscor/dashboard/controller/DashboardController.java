package com.vivo4redes.syscor.dashboard.controller;

import com.vivo4redes.syscor.dashboard.dto.DashboardExecutivoDTO;
import com.vivo4redes.syscor.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/executivo")
    public ResponseEntity<DashboardExecutivoDTO> obterPainelExecutivo(
            @RequestParam(required = false) Long filialId
    ) {
        return ResponseEntity.ok(dashboardService.obterPainelExecutivo(filialId));
    }
}