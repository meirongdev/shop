package dev.meirong.shop.promotion.service;

import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import dev.meirong.shop.promotion.domain.PromotionOfferRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionApplicationService {

    private final PromotionOfferRepository repository;

    public PromotionApplicationService(PromotionOfferRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PromotionApi.OffersView listOffers(PromotionApi.ListOffersRequest request) {
        return new PromotionApi.OffersView(repository.findByActiveTrueOrderByCreatedAtDesc().stream().map(this::toResponse).toList());
    }

    @Transactional
    public PromotionApi.OfferResponse createOffer(PromotionApi.CreateOfferRequest request) {
        PromotionOfferEntity entity = new PromotionOfferEntity(
                request.code(),
                request.title(),
                request.description(),
                request.rewardAmount(),
                true,
                request.sellerId()
        );
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void createWalletRewardOffer(String code, String title, String description, java.math.BigDecimal rewardAmount) {
        if (repository.existsByCode(code)) {
            return;
        }
        repository.save(new PromotionOfferEntity(code, title, description, rewardAmount, true, "wallet-reward"));
    }

    @Transactional(readOnly = true)
    public long countActiveOffersForSeller(String sellerId) {
        return repository.countBySourceAndActiveTrue(sellerId);
    }

    @Transactional(readOnly = true)
    public List<PromotionApi.OfferResponse> listOffersForSeller(String sellerId) {
        return repository.findBySourceOrderByCreatedAtDesc(sellerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void voidCoupon(String couponCode) {
        repository.findByCode(couponCode).ifPresent(offer -> {
            offer.deactivate();
            repository.save(offer);
        });
    }

    @Transactional
    public void rollbackOffer(String offerId) {
        repository.findById(offerId).ifPresent(offer -> {
            offer.deactivate();
            repository.save(offer);
        });
    }

    private PromotionApi.OfferResponse toResponse(PromotionOfferEntity entity) {
        return new PromotionApi.OfferResponse(
                UUID.fromString(entity.getId()),
                entity.getCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getRewardAmount(),
                entity.isActive(),
                entity.getSource(),
                entity.getCreatedAt()
        );
    }
}
