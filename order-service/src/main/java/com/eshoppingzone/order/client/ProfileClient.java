package com.eshoppingzone.order.client;

import com.eshoppingzone.order.dto.AddressDto;
import com.eshoppingzone.order.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "profile-service")
public interface ProfileClient {

    @GetMapping("/api/v1/profiles/addresses/{id}/internal")
    ApiResponse<AddressDto> getAddressById(@PathVariable("id") Long id);
}
