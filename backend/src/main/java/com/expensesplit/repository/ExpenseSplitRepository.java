package com.expensesplit.repository;

import com.expensesplit.entity.ExpenseSplit;
import com.expensesplit.entity.ExpenseSplitId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, ExpenseSplitId> {
    List<ExpenseSplit> findByExpenseGroupId(Long groupId);
}
