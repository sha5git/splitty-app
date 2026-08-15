package com.expensesplit.controller;

import com.expensesplit.dto.CreateExpenseRequest;
import com.expensesplit.dto.ExpenseDto;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.ExpenseService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/groups/{id}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto createExpense(@PathVariable("id") Long groupId,
                                    @Valid @RequestBody CreateExpenseRequest request,
                                    @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return expenseService.createExpense(groupId, request, principal);
    }

    @GetMapping("/groups/{id}/expenses")
    public List<ExpenseDto> listExpenses(@PathVariable("id") Long groupId,
                                         @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return expenseService.listExpenses(groupId, principal);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteExpense(@PathVariable("id") Long expenseId,
                              @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        expenseService.deleteExpense(expenseId, principal);
    }
}
