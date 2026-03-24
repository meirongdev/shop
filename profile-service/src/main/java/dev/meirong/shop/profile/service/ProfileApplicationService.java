package dev.meirong.shop.profile.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.profile.domain.BuyerProfileEntity;
import dev.meirong.shop.profile.domain.BuyerProfileRepository;
import dev.meirong.shop.profile.domain.SellerProfileEntity;
import dev.meirong.shop.profile.domain.SellerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileApplicationService {

    private final BuyerProfileRepository repository;
    private final SellerProfileRepository sellerRepository;

    public ProfileApplicationService(BuyerProfileRepository repository, SellerProfileRepository sellerRepository) {
        this.repository = repository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional(readOnly = true)
    public ProfileApi.ProfileResponse getProfile(ProfileApi.GetProfileRequest request) {
        return repository.findById(request.playerId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Profile not found: " + request.playerId()));
    }

    @Transactional
    public ProfileApi.ProfileResponse updateProfile(ProfileApi.UpdateProfileRequest request) {
        BuyerProfileEntity entity = repository.findById(request.playerId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Profile not found: " + request.playerId()));
        entity.update(request.displayName(), request.email(), request.tier());
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public ProfileApi.ProfileResponse getSellerProfile(ProfileApi.GetProfileRequest request) {
        return sellerRepository.findById(request.playerId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Seller profile not found: " + request.playerId()));
    }

    @Transactional
    public ProfileApi.ProfileResponse updateSellerProfile(ProfileApi.UpdateProfileRequest request) {
        SellerProfileEntity entity = sellerRepository.findById(request.playerId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Seller profile not found: " + request.playerId()));
        entity.update(request.displayName(), request.email(), request.tier());
        return toResponse(sellerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ProfileApi.SellerStorefrontResponse getSellerStorefront(String sellerId) {
        SellerProfileEntity entity = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Seller not found: " + sellerId));
        return toStorefrontResponse(entity);
    }

    @Transactional
    public ProfileApi.SellerStorefrontResponse updateShop(ProfileApi.UpdateShopRequest request) {
        SellerProfileEntity entity = sellerRepository.findById(request.sellerId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Seller not found: " + request.sellerId()));
        entity.updateShop(request.shopName(), request.shopSlug(), request.shopDescription(),
                request.logoUrl(), request.bannerUrl());
        return toStorefrontResponse(sellerRepository.save(entity));
    }

    private ProfileApi.ProfileResponse toResponse(BuyerProfileEntity entity) {
        return new ProfileApi.ProfileResponse(
                entity.getPlayerId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getTier(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ProfileApi.ProfileResponse toResponse(SellerProfileEntity entity) {
        return new ProfileApi.ProfileResponse(
                entity.getSellerId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getTier(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ProfileApi.SellerStorefrontResponse toStorefrontResponse(SellerProfileEntity entity) {
        return new ProfileApi.SellerStorefrontResponse(
                entity.getSellerId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getShopName(),
                entity.getShopSlug(),
                entity.getShopDescription(),
                entity.getLogoUrl(),
                entity.getBannerUrl(),
                entity.getAvgRating(),
                entity.getTotalSales(),
                entity.getTier(),
                entity.getCreatedAt()
        );
    }
}
