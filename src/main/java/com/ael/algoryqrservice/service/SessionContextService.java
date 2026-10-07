package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionContextService {

    private final UserRepository userRepository;
    private final ObjectProvider<FulfillmentServiceClient> fulfillmentServiceClient;
    private final SessionContextAssembler assembler;

    public SessionContextResponse resolve(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        FulfillmentServiceClient client = client();
        PackageView view = client.findPackage(user.getId());
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return assembler.blocked(
                    user,
                    AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null)
            );
        }
        return assembler.paid(user, view.body(), client.listEntitlements(user.getId()));
    }

    private FulfillmentServiceClient client() {
        FulfillmentServiceClient client = fulfillmentServiceClient.getIfAvailable();
        if (client == null) {
            throw new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
        }
        return client;
    }
}
