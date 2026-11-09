package com.algoryqr.staff.feature.flow

import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.staff.data.StaffRepository

/**
 * The branch's order flow, which decides the tabs and buttons staff see. Re-read every minute so a flow the owner
 * changes reaches devices already on shift.
 */
class BranchFlowViewModel(private val repository: StaffRepository) : LoadViewModel<BranchFlow>(pollMillis = 60_000) {

    init {
        start()
    }

    override suspend fun fetch(): BranchFlow = repository.orderFlow()
}
