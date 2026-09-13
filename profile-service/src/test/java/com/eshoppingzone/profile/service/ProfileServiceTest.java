package com.eshoppingzone.profile.service;

import com.eshoppingzone.profile.dto.AddressDto;
import com.eshoppingzone.profile.dto.AddressRequest;
import com.eshoppingzone.profile.dto.UpdateProfileRequest;
import com.eshoppingzone.profile.dto.UserProfileDto;
import com.eshoppingzone.profile.entity.Address;
import com.eshoppingzone.profile.entity.UserProfile;
import com.eshoppingzone.profile.exception.ResourceNotFoundException;
import com.eshoppingzone.profile.repository.AddressRepository;
import com.eshoppingzone.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private UserProfile sampleProfile;
    private Address sampleAddress;

    @BeforeEach
    void setUp() {
        sampleProfile = new UserProfile(1L, 100L, "john", "john@example.com", "John Doe", "1234567890", LocalDate.of(1990, 1, 1), "MALE");
        sampleAddress = new Address(1L, 100L, "123 Main St", "City", "State", "12345", "USA", "HOME", true);
    }

    @Test
    void testGetMyProfileFound() {
        when(userProfileRepository.findByUserId(100L)).thenReturn(Optional.of(sampleProfile));

        UserProfileDto dto = profileService.getMyProfile(100L, "john", "john@example.com");

        assertNotNull(dto);
        assertEquals("John Doe", dto.getFullName());
        assertEquals("john@example.com", dto.getEmail());
    }

    @Test
    void testUpdateMyProfileSuccess() {
        UpdateProfileRequest request = new UpdateProfileRequest("John Updated", "9999999999", LocalDate.of(1990, 1, 1), "MALE");
        when(userProfileRepository.findByUserId(100L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(sampleProfile);

        UserProfileDto dto = profileService.updateMyProfile(100L, request);

        assertNotNull(dto);
        verify(userProfileRepository, times(1)).save(sampleProfile);
    }

    @Test
    void testAddAddressFirstAddressIsDefault() {
        AddressRequest request = new AddressRequest("456 Elm St", "City2", "State2", "67890", "USA", "HOME", false);
        when(addressRepository.countByUserId(100L)).thenReturn(0L);
        when(addressRepository.save(any(Address.class))).thenReturn(sampleAddress);

        AddressDto dto = profileService.addAddress(100L, request);

        assertNotNull(dto);
        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    void testGetAddressByIdNotFoundThrowsException() {
        when(addressRepository.findByIdAndUserId(999L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> profileService.getAddressById(999L, 100L));
    }

    @Test
    void testGetMyAddresses() {
        when(addressRepository.findByUserId(100L)).thenReturn(List.of(sampleAddress));

        List<AddressDto> addresses = profileService.getMyAddresses(100L);

        assertEquals(1, addresses.size());
        assertEquals("123 Main St", addresses.get(0).getStreet());
    }
}
