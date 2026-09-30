package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenCloseGuardTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private StoreOrderRepository storeOrderRepository;
    @Mock
    private KitchenUberEatsService kitchenUberEatsService;
    @Mock
    private KitchenYemekSepetiService kitchenYemekSepetiService;

    @InjectMocks
    private KitchenCloseGuard guard;

    private final Branch branch = Branch.builder().id(15L).userId(7L).name("Kadıköy").kitchenEnabled(true).build();

    @BeforeEach
    void setUp() {
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR))
                .thenReturn(List.of(Menu.builder().menuId(4L).branchId(15L).build()));
    }

    @Test
    void rejectsWhenBranchHasActiveTableOrder() {
        when(menuOrderRepository.existsByMenuIdInAndStatusIn(anyList(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> guard.requireNoActiveOrders(branch))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Aktif olan siparişleriniz var, mutfağı kapatamazsınız");
    }

    @Test
    void rejectsWhenStoreOrderBelongsToBranch() {
        when(menuOrderRepository.existsByMenuIdInAndStatusIn(anyList(), anyCollection())).thenReturn(false);
        when(merchantRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(Merchant.builder().id(3L).build()));
        when(storeOrderRepository.existsForBranch(eq(3L), eq(15L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> guard.requireNoActiveOrders(branch)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void allowsWhenIntegrationOrdersBelongToAnotherBranch() {
        when(menuOrderRepository.existsByMenuIdInAndStatusIn(anyList(), anyCollection())).thenReturn(false);
        when(merchantRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        when(kitchenUberEatsService.listActiveForBranch(7L, 15L)).thenReturn(List.of());
        when(kitchenYemekSepetiService.listActiveForBranch(7L, 15L)).thenReturn(List.of());

        assertThatCode(() -> guard.requireNoActiveOrders(branch)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenIntegrationOrderIsInThisBranchKitchen() {
        when(menuOrderRepository.existsByMenuIdInAndStatusIn(anyList(), anyCollection())).thenReturn(false);
        when(merchantRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        when(kitchenUberEatsService.listActiveForBranch(7L, 15L))
                .thenReturn(List.of(mock(MenuOrderDtos.OrderResponse.class)));

        assertThatThrownBy(() -> guard.requireNoActiveOrders(branch)).isInstanceOf(BadRequestException.class);
    }
}
