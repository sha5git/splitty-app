package com.expensesplit.controller;

import com.expensesplit.dto.CreateSettlementRequest;
import com.expensesplit.dto.SettlementDto;
import com.expensesplit.dto.UpdateSettlementRequest;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.SettlementService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/groups/{id}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementDto recordSettlement(@PathVariable("id") Long groupId,
                                           @Valid @RequestBody CreateSettlementRequest request,
                                           @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return settlementService.recordSettlement(groupId, request, principal);
    }

    @GetMapping("/groups/{id}/settlements")
    public List<SettlementDto> listSettlements(@PathVariable("id") Long groupId,
                                               @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return settlementService.listSettlements(groupId, principal);
    }

    @PutMapping("/settlements/{id}")
    public SettlementDto updateSettlement(@PathVariable("id") Long settlementId,
                                          @Valid @RequestBody UpdateSettlementRequest request,
                                          @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return settlementService.updateSettlement(settlementId, request, principal);
    }
}
