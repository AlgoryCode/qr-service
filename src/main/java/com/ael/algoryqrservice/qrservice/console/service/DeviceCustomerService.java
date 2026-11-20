package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Customer cards: prepaid balance and loyalty minutes, shared by every branch of the merchant. A top-up is money
 * held for the customer, not income; the income is booked when a session is paid from it.
 */
@Service
@RequiredArgsConstructor
public class DeviceCustomerService {

    private final DeviceCustomerRepository customerRepository;
    private final DeviceCustomerTxnRepository txnRepository;
    private final ConsoleAccess access;

    @Transactional(readOnly = true)
    public ConsoleDtos.CustomerPageResponse search(String query, int page, int size) {
        String q = query == null ? "" : query.trim();
        // A phone typed with spaces or dashes should still match the stored digits.
        String term = q.matches("[0-9+()\\s-]+") ? DeviceMappers.normalizePhone(q) : q.toLowerCase(Locale.forLanguageTag("tr"));
        Page<DeviceCustomer> result = customerRepository.search(
                access.merchantId(),
                term == null ? "" : term,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
        );
        return new ConsoleDtos.CustomerPageResponse(
                result.getContent().stream().map(DeviceMappers::customer).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.CustomerResponse get(Long customerId) {
        return DeviceMappers.customer(requireOwned(customerId));
    }

    @Transactional
    public ConsoleDtos.CustomerResponse create(ConsoleDtos.CustomerRequest request) {
        Long merchantId = access.merchantId();
        String phone = DeviceMappers.normalizePhone(request.phone());
        if (phone != null && customerRepository.existsByMerchantIdAndPhoneAndDeletedFalse(merchantId, phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefonla kayıtlı bir müşteri var");
        }
        DeviceCustomer saved = customerRepository.save(DeviceCustomer.builder()
                .merchantId(merchantId)
                .name(request.name().trim())
                .phone(phone)
                .note(DeviceMappers.blankToNull(request.note()))
                .balance(BigDecimal.ZERO.setScale(2))
                .totalSpent(BigDecimal.ZERO.setScale(2))
                .deleted(false)
                .build());
        return DeviceMappers.customer(saved);
    }

    @Transactional
    public ConsoleDtos.CustomerResponse update(Long customerId, ConsoleDtos.CustomerRequest request) {
        DeviceCustomer customer = requireOwned(customerId);
        String phone = DeviceMappers.normalizePhone(request.phone());
        if (phone != null && customerRepository.existsByMerchantIdAndPhoneAndDeletedFalseAndIdNot(
                customer.getMerchantId(), phone, customer.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefonla kayıtlı bir müşteri var");
        }
        customer.setName(request.name().trim());
        customer.setPhone(phone);
        customer.setNote(DeviceMappers.blankToNull(request.note()));
        return DeviceMappers.customer(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long customerId) {
        DeviceCustomer customer = requireOwned(customerId);
        if (customer.getBalance().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Müşterinin bakiyesi var; önce bakiyeyi sıfırlayın");
        }
        customer.setDeleted(true);
        customerRepository.save(customer);
    }

    @Transactional
    public ConsoleDtos.CustomerResponse topUp(Long customerId, ConsoleDtos.TopUpRequest request) {
        DevicePaymentMethod method = request.paymentMethod() == null ? DevicePaymentMethod.CASH : request.paymentMethod();
        if (method == DevicePaymentMethod.BALANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakiye yüklemesi nakit, kart veya havale ile yapılır");
        }
        DeviceCustomer customer = lockOwned(customerId);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        customer.setBalance(customer.getBalance().add(amount));
        txnRepository.save(DeviceCustomerTxn.builder()
                .merchantId(customer.getMerchantId())
                .customerId(customer.getId())
                .txnType(DeviceCustomerTxnType.TOPUP)
                .amount(amount)
                .paymentMethod(method)
                .note(DeviceMappers.blankToNull(request.note()))
                .deleted(false)
                .build());
        return DeviceMappers.customer(customerRepository.save(customer));
    }

    @Transactional
    public ConsoleDtos.CustomerResponse adjust(Long customerId, ConsoleDtos.AdjustRequest request) {
        BigDecimal balanceDelta = request.balanceDelta() == null ? BigDecimal.ZERO : request.balanceDelta();
        int minutesDelta = request.freeMinutesDelta() == null ? 0 : request.freeMinutesDelta();
        if (balanceDelta.signum() == 0 && minutesDelta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Değişiklik girin");
        }
        DeviceCustomer customer = lockOwned(customerId);
        BigDecimal balance = customer.getBalance().add(balanceDelta).setScale(2, RoundingMode.HALF_UP);
        int minutes = customer.getFreeMinutes() + minutesDelta;
        if (balance.signum() < 0 || minutes < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakiye veya ücretsiz dakika eksiye düşemez");
        }
        customer.setBalance(balance);
        customer.setFreeMinutes(minutes);
        String note = DeviceMappers.blankToNull(request.note());
        if (minutesDelta != 0) {
            String minutesNote = (minutesDelta > 0 ? "+" : "") + minutesDelta + " dk ücretsiz";
            note = note == null ? minutesNote : note + " · " + minutesNote;
        }
        txnRepository.save(DeviceCustomerTxn.builder()
                .merchantId(customer.getMerchantId())
                .customerId(customer.getId())
                .txnType(DeviceCustomerTxnType.ADJUST)
                .amount(balanceDelta.setScale(2, RoundingMode.HALF_UP))
                .note(note == null ? null : note.substring(0, Math.min(note.length(), 255)))
                .deleted(false)
                .build());
        return DeviceMappers.customer(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.CustomerTxnPageResponse transactions(Long customerId, int page, int size) {
        DeviceCustomer customer = requireOwned(customerId);
        var result = txnRepository.findByCustomerIdAndMerchantIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
                customer.getId(),
                customer.getMerchantId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
        );
        return new ConsoleDtos.CustomerTxnPageResponse(
                result.getContent().stream().map(DeviceMappers::txn).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private DeviceCustomer requireOwned(Long customerId) {
        return customerRepository.findByIdAndMerchantIdAndDeletedFalse(customerId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
    }

    private DeviceCustomer lockOwned(Long customerId) {
        return customerRepository.lockOwned(customerId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
    }
}
