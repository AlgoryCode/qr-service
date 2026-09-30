package com.ael.algoryqrservice.service;

/** Şubesi boş kayıt (eski sipariş, şube atanmamış bağlantı) tüm şubelere düşer. */
public final class BranchScope {

    private BranchScope() {
    }

    public static boolean serves(Long ownerBranchId, Long branchId) {
        return ownerBranchId == null || branchId == null || ownerBranchId.equals(branchId);
    }
}
