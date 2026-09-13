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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;

    public ProfileServiceImpl(UserProfileRepository userProfileRepository, AddressRepository addressRepository) {
        this.userProfileRepository = userProfileRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public UserProfileDto getMyProfile(Long userId, String username, String email) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile newProfile = new UserProfile();
                    newProfile.setUserId(userId);
                    newProfile.setUsername(username != null ? username : "user_" + userId);
                    newProfile.setEmail(email != null ? email : "user_" + userId + "@eshoppingzone.com");
                    newProfile.setFullName(username != null ? username : "User " + userId);
                    return userProfileRepository.save(newProfile);
                });
        return UserProfileDto.fromEntity(profile);
    }

    @Override
    @Transactional
    public UserProfileDto updateMyProfile(Long userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user ID: " + userId));

        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setGender(request.getGender());

        UserProfile updated = userProfileRepository.save(profile);
        return UserProfileDto.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getProfileByUserId(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user ID: " + userId));
        return UserProfileDto.fromEntity(profile);
    }

    @Override
    @Transactional
    public AddressDto addAddress(Long userId, AddressRequest request) {
        long addressCount = addressRepository.countByUserId(userId);
        boolean shouldBeDefault = request.isDefault() || addressCount == 0;

        if (shouldBeDefault) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(addr -> {
                addr.setDefault(false);
                addressRepository.save(addr);
            });
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setAddressType(request.getAddressType() != null ? request.getAddressType() : "HOME");
        address.setDefault(shouldBeDefault);

        Address saved = addressRepository.save(address);
        return AddressDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressDto> getMyAddresses(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(AddressDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AddressDto getAddressById(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));
        return AddressDto.fromEntity(address);
    }

    @Override
    @Transactional
    public AddressDto updateAddress(Long id, Long userId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        if (request.isDefault() && !address.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(addr -> {
                addr.setDefault(false);
                addressRepository.save(addr);
            });
            address.setDefault(true);
        }

        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }

        Address saved = addressRepository.save(address);
        return AddressDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteAddress(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUserId(userId);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Override
    @Transactional
    public AddressDto setDefaultAddress(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(addr -> {
            addr.setDefault(false);
            addressRepository.save(addr);
        });

        address.setDefault(true);
        Address saved = addressRepository.save(address);
        return AddressDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressDto getAddressInternal(Long id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));
        return AddressDto.fromEntity(address);
    }
}
