package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.campaign.service.CampaignEvaluationService;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.MenuOrder;
import com.ael.algoryqrservice.qrservice.model.RestaurantTable;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.ael.algoryqrservice.qrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuOrderServiceListTest {

    @Mock private MenuOrderRepository menuOrderRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private RestaurantTableRepository restaurantTableRepository;
    @Mock private com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository merchantStaffRepository;
    @Mock private com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository.CustomerRepository customerRepository;
    @Mock private CampaignEvaluationService campaignEvaluationService;
    @Mock private KitchenUberEatsService kitchenUberEatsService;
    @Mock private KitchenYemekSepetiService kitchenYemekSepetiService;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private MenuOrderService menuOrderService;

    private MenuOrder order(long id, Long tableId) {
        MenuOrder order = new MenuOrder();
        order.setId(id);
        order.setMenuId(8L);
        order.setTableId(tableId);
        return order;
    }

    @Test
    void merchantList_readsRelatedRowsOnceForTheWholeList() {
        Menu menu = new Menu();
        menu.setMerchantId(7L);
        when(menuRepository.findById(8L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        when(menuOrderRepository.findByMenuIdAndStatusInOrderBySubmittedAtDesc(eq(8L), any()))
                .thenReturn(List.of(order(1L, 5L), order(2L, 5L), order(3L, null)));
        RestaurantTable table = mock(RestaurantTable.class);
        when(table.getId()).thenReturn(5L);
        when(table.getName()).thenReturn("Masa 5");
        when(restaurantTableRepository.findAllById(Set.of(5L))).thenReturn(List.of(table));

        List<MenuOrderDtos.OrderResponse> orders = menuOrderService.merchantList(8L, "ALL", null);

        assertThat(orders).extracting(MenuOrderDtos.OrderResponse::getTableName)
                .containsExactly("Masa 5", "Masa 5", "Masa yok");
        verify(restaurantTableRepository, times(1)).findAllById(Set.of(5L));
        verify(restaurantTableRepository, never()).findById(any());
        verify(campaignEvaluationService, times(1)).summarizeOrders(eq(8L), anyList());
        verify(campaignEvaluationService, never()).summarizeOrder(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void merchantList_listsMarketplaceOrdersOnlyUnderTheMenuOfTheConnectionBranch() {
        Menu branchMenu = menu(16L, 24L);
        Menu otherMenu = menu(8L, 5L);
        when(menuRepository.findById(16L)).thenReturn(Optional.of(branchMenu));
        when(menuRepository.findById(8L)).thenReturn(Optional.of(otherMenu));
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        when(menuRepository.findByMerchantIdAndChannelAndDeletedFalseOrderByMenuIdAsc(eq(7L), any()))
                .thenReturn(List.of(otherMenu, branchMenu));

        menuOrderService.merchantList(16L, "ALL", null);
        org.mockito.ArgumentCaptor<java.util.function.Predicate<Long>> forBranchMenu =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Predicate.class);
        verify(kitchenUberEatsService).listManagedForOwner(eq(7L), forBranchMenu.capture());

        assertThat(forBranchMenu.getValue().test(24L)).isTrue();
        assertThat(forBranchMenu.getValue().test(5L)).isFalse();
        // No menu in branch 1 and no branch at all: the first menu (8) takes them, not this one.
        assertThat(forBranchMenu.getValue().test(1L)).isFalse();
        assertThat(forBranchMenu.getValue().test(null)).isFalse();

        org.mockito.Mockito.clearInvocations(kitchenUberEatsService);
        menuOrderService.merchantList(8L, "ALL", null);
        org.mockito.ArgumentCaptor<java.util.function.Predicate<Long>> forFirstMenu =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Predicate.class);
        verify(kitchenUberEatsService).listManagedForOwner(eq(7L), forFirstMenu.capture());

        assertThat(forFirstMenu.getValue().test(24L)).isFalse();
        assertThat(forFirstMenu.getValue().test(5L)).isTrue();
        assertThat(forFirstMenu.getValue().test(1L)).isTrue();
        assertThat(forFirstMenu.getValue().test(null)).isTrue();
    }

    private Menu menu(long menuId, long branchId) {
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setMerchantId(7L);
        menu.setBranchId(branchId);
        return menu;
    }
}
