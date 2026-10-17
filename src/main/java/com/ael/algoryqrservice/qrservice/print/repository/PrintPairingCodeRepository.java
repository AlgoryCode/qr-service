package com.ael.algoryqrservice.qrservice.print.repository;

import com.ael.algoryqrservice.qrservice.print.model.PrintPairingCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrintPairingCodeRepository extends JpaRepository<PrintPairingCode, Long> {

    Optional<PrintPairingCode> findByCodeHash(String codeHash);
}
