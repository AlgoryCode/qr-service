package com.ael.algoryqrservice.print.service;

import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.print.repository.PrintAgentDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrintDeviceLookupService {

    private final PrintAgentDeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public Optional<PrintAgentDevice> findEnabledByTokenHash(String tokenHash) {
        return deviceRepository.findByDeviceTokenHashAndEnabledTrue(tokenHash);
    }
}
