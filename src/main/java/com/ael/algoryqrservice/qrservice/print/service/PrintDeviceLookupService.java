package com.ael.algoryqrservice.qrservice.print.service;

import com.ael.algoryqrservice.qrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.qrservice.print.repository.PrintAgentDeviceRepository;
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
