package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.client.ActivePackageLookup;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionContextService {

    private static final String ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final SessionAccessService sessionAccessService;
    private final ObjectProvider<FulfillmentServiceClient> fulfillmentServiceClient;
    private final SessionContextAssembler assembler;

    public SessionContextResponse resolve(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        FulfillmentServiceClient client = fulfillmentServiceClient.getIfAvailable();
        if (client != null) {
            SessionContextResponse remote = fromFulfillment(user, client);
            if (remote != null) {
                return remote;
            }
        }
        return fromLocal(user, userId);
    }

    private SessionContextResponse fromFulfillment(User user, FulfillmentServiceClient client) {
        ActivePackageLookup lookup = client.lookupActivePackage(user.getId());
        if (!(lookup instanceof ActivePackageLookup.Found found)) {
            return null;
        }
        if (!isActive(found.value())) {
            return null;
        }
        return assembler.paid(user, found.value(), client.listEntitlements(user.getId()));
    }

    private SessionContextResponse fromLocal(User user, Long userId) {
        AccessSession session = sessionAccessService.resolve(userId);
        if (!session.isAllow()) {
            return assembler.blocked(user, session);
        }
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        return assembler.localAllow(user, session, details);
    }

    private boolean isActive(ExternalActivePackageResponse response) {
        return ACTIVE.equals(response.status());
    }
}
