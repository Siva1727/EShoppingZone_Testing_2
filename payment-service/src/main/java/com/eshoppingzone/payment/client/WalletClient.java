package com.eshoppingzone.payment.client;

import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.WalletDto;
import com.eshoppingzone.payment.dto.WalletTransferRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service")
public interface WalletClient {

    @PostMapping("/api/v1/wallet/debit")
    ApiResponse<WalletDto> debit(@RequestBody WalletTransferRequest request);

    @PostMapping("/api/v1/wallet/credit")
    ApiResponse<WalletDto> credit(@RequestBody WalletTransferRequest request);
}
