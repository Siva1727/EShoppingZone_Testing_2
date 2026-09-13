package com.eshoppingzone.profile.service;

import com.eshoppingzone.profile.dto.AddressDto;
import com.eshoppingzone.profile.dto.AddressRequest;
import com.eshoppingzone.profile.dto.UpdateProfileRequest;
import com.eshoppingzone.profile.dto.UserProfileDto;

import java.util.List;

public interface ProfileService {
    UserProfileDto getMyProfile(Long userId, String username, String email);
    UserProfileDto updateMyProfile(Long userId, UpdateProfileRequest request);
    UserProfileDto getProfileByUserId(Long userId);

    AddressDto addAddress(Long userId, AddressRequest request);
    List<AddressDto> getMyAddresses(Long userId);
    AddressDto getAddressById(Long id, Long userId);
    AddressDto updateAddress(Long id, Long userId, AddressRequest request);
    void deleteAddress(Long id, Long userId);
    AddressDto setDefaultAddress(Long id, Long userId);
    AddressDto getAddressInternal(Long id);
}
